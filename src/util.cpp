/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/util.h"
#include <avian/util/list.h>

using namespace vm;

namespace {

class TreeContext {
 public:

  class MyProtector: public Thread::Protector {
   public:
    MyProtector(Thread* thread, TreeContext* context):
      Protector(thread), context(context)
    { }

    virtual void visit(Heap::Visitor* v) {
      v->visit(&(context->root));
      v->visit(&(context->node));

      for (List<object>* p = context->ancestors; p; p = p->next) {
        v->visit(&(p->item));
      }
    }

    TreeContext* context;
  };

  TreeContext(Thread* thread, Zone* zone):
    zone(zone), root(0), node(0), ancestors(0), protector(thread, this),
    fresh(false)
  { }
  
  Zone* zone;
  object root;
  object node;
  List<object>* ancestors;
  MyProtector protector;
  bool fresh;
};

List<object>*
path(TreeContext* c, object node, List<object>* next)
{
  return new(c->zone) List<object>(node, next);
}

inline object
getTreeNodeValue(Thread*, object n)
{
  return reinterpret_cast<object>
    (alias(n, TreeNodeValue) & PointerMask);
}

inline void
setTreeNodeValue(Thread* t, object n, object value)
{
  intptr_t red = alias(n, TreeNodeValue) & (~PointerMask);

  set(t, n, TreeNodeValue, value);

  alias(n, TreeNodeValue) |= red;
}

inline bool
treeNodeRed(Thread*, object n)
{
  return (alias(n, TreeNodeValue) & (~PointerMask)) == 1;
}

inline void
setTreeNodeRed(Thread*, object n, bool red)
{
  if (red) {
    alias(n, TreeNodeValue) |= 1;
  } else {
    alias(n, TreeNodeValue) &= PointerMask;
  }
}

inline object
cloneTreeNode(Thread* t, object n)
{
  PROTECT(t, n);

  object newNode = reinterpret_cast<object>(makeTreeNode
    (t, getTreeNodeValue(t, n), treeNodeLeft(t, n), treeNodeRight(t, n)));
  setTreeNodeRed(t, newNode, treeNodeRed(t, n));
  return newNode;
}

object
treeFind(Thread* t, object tree, intptr_t key, object sentinal,
         intptr_t (*compare)(Thread* t, intptr_t key, object b))
{
  object node = tree;
  while (node != sentinal) {
    intptr_t difference = compare(t, key, getTreeNodeValue(t, node));
    if (difference < 0) {
      node = treeNodeLeft(t, node);
    } else if (difference > 0) {
      node = treeNodeRight(t, node);
    } else {
      return node;
    }
  }

  return 0;
}

void
treeFind(Thread* t, TreeContext* c, object old, intptr_t key, object node,
         object sentinal,
         intptr_t (*compare)(Thread* t, intptr_t key, object b))
{
  PROTECT(t, old);
  PROTECT(t, node);
  PROTECT(t, sentinal);

  object newRoot = cloneTreeNode(t, old);
  PROTECT(t, newRoot);

  object new_ = newRoot;
  PROTECT(t, new_);

  int count = 0;
  while (old != sentinal) {
    c->ancestors = path(c, new_, c->ancestors);

    intptr_t difference = compare(t, key, getTreeNodeValue(t, old));

    if (difference < 0) {
      old = treeNodeLeft(t, old);
      object n = cloneTreeNode(t, old);
      set(t, new_, TreeNodeLeft, n);
      new_ = n;
    } else if (difference > 0) {
      old = treeNodeRight(t, old);
      object n = cloneTreeNode(t, old);
      set(t, new_, TreeNodeRight, n);
      new_ = n;
    } else {
      c->fresh = false;
      c->root = newRoot;
      c->node = new_;
      c->ancestors = c->ancestors->next;
      return;
    }

    if (++ count > 100) {
      // if we've gone this deep, we probably have an unbalanced tree,
      // which should only happen if there's a serious bug somewhere
      // in our insertion process
      abort(t);
    }
  }

  setTreeNodeValue(t, new_, getTreeNodeValue(t, node));

  c->fresh = true;
  c->root = newRoot;
  c->node = new_;
  c->ancestors = c->ancestors;
}

object
leftRotate(Thread* t, object n)
{
  PROTECT(t, n);

  object child = cloneTreeNode(t, treeNodeRight(t, n));
  set(t, n, TreeNodeRight, treeNodeLeft(t, child));
  set(t, child, TreeNodeLeft, n);
  return child;
}

object
rightRotate(Thread* t, object n)
{
  PROTECT(t, n);

  object child = cloneTreeNode(t, treeNodeLeft(t, n));
  set(t, n, TreeNodeLeft, treeNodeRight(t, child));
  set(t, child, TreeNodeRight, n);
  return child;
}

object
treeAdd(Thread* t, TreeContext* c)
{
  object new_ = c->node;
  PROTECT(t, new_);

  object newRoot = c->root;
  PROTECT(t, newRoot);

  // rebalance
  setTreeNodeRed(t, new_, true);
  while (c->ancestors != 0 and treeNodeRed(t, c->ancestors->item)) {
    if (c->ancestors->item
        == treeNodeLeft(t, c->ancestors->next->item))
    {
      if (treeNodeRed
          (t, treeNodeRight(t, c->ancestors->next->item)))
      {
        setTreeNodeRed(t, c->ancestors->item, false);

        object n = cloneTreeNode
          (t, treeNodeRight(t, c->ancestors->next->item));

        set(t, c->ancestors->next->item, TreeNodeRight, n);

        setTreeNodeRed(t, treeNodeRight(t, c->ancestors->next->item), false);

        setTreeNodeRed(t, c->ancestors->next->item, true);

        new_ = c->ancestors->next->item;
        c->ancestors = c->ancestors->next->next;
      } else {
        if (new_ == treeNodeRight(t, c->ancestors->item)) {
          new_ = c->ancestors->item;
          c->ancestors = c->ancestors->next;

          object n = leftRotate(t, new_);

          if (new_ == treeNodeRight(t, c->ancestors->item)) {
            set(t, c->ancestors->item, TreeNodeRight, n);
          } else {
            set(t, c->ancestors->item, TreeNodeLeft, n);
          }
          c->ancestors = path(c, n, c->ancestors);
        }
        setTreeNodeRed(t, c->ancestors->item, false);
        setTreeNodeRed(t, c->ancestors->next->item, true);

        object n = rightRotate(t, c->ancestors->next->item);
        if (c->ancestors->next->next == 0) {
          newRoot = n;
        } else if (treeNodeRight(t, c->ancestors->next->next->item)
                   == c->ancestors->next->item)
        {
          set(t, c->ancestors->next->next->item, TreeNodeRight, n);
        } else {
          set(t, c->ancestors->next->next->item, TreeNodeLeft, n);
        }
        // done
      }
    } else { // this is just the reverse of the code above (right and
             // left swapped):
      if (treeNodeRed
          (t, treeNodeLeft(t, c->ancestors->next->item)))
      {
        setTreeNodeRed(t, c->ancestors->item, false);

        object n = cloneTreeNode
          (t, treeNodeLeft(t, c->ancestors->next->item));

        set(t, c->ancestors->next->item, TreeNodeLeft, n);

        setTreeNodeRed(t, treeNodeLeft(t, c->ancestors->next->item), false);

        setTreeNodeRed(t, c->ancestors->next->item, true);

        new_ = c->ancestors->next->item;
        c->ancestors = c->ancestors->next->next;
      } else {
        if (new_ == treeNodeLeft(t, c->ancestors->item)) {
          new_ = c->ancestors->item;
          c->ancestors = c->ancestors->next;

          object n = rightRotate(t, new_);

          if (new_ == treeNodeLeft(t, c->ancestors->item)) {
            set(t, c->ancestors->item, TreeNodeLeft, n);
          } else {
            set(t, c->ancestors->item, TreeNodeRight, n);
          }
          c->ancestors = path(c, n, c->ancestors);
        }
        setTreeNodeRed(t, c->ancestors->item, false);
        setTreeNodeRed(t, c->ancestors->next->item, true);

        object n = leftRotate(t, c->ancestors->next->item);
        if (c->ancestors->next->next == 0) {
          newRoot = n;
        } else if (treeNodeLeft(t, c->ancestors->next->next->item)
                   == c->ancestors->next->item)
        {
          set(t, c->ancestors->next->next->item, TreeNodeLeft, n);
        } else {
          set(t, c->ancestors->next->next->item, TreeNodeRight, n);
        }
        // done
      }
    }
  }

  setTreeNodeRed(t, newRoot, false);

  return newRoot;
}

} // namespace

namespace vm {

object
hashMapFindNode(Thread* t, GcHashMap* map, object key,
                uint32_t (*hash)(Thread*, object),
                bool (*equal)(Thread*, object, object))
{
  bool weak = objectClass(t, map) == type(t, GcWeakHashMap::Type);

  object array = map->array();
  if (array) {
    unsigned index = hash(t, key) & (arrayLength(t, array) - 1);
    for (object n = arrayBody(t, array, index); n; n = tripleThird(t, n)) {
      object k = tripleFirst(t, n);
      if (weak) {
        k = jreferenceTarget(t, k);
        if (k == 0) {
          continue;
        }
      }

      if (equal(t, key, k)) {
        return n;
      }
    }
  }
  return 0;
}

void
hashMapResize(Thread* t, GcHashMap* map, uint32_t (*hash)(Thread*, object),
              unsigned size)
{
  PROTECT(t, map);

  object newArray = 0;

  if (size) {
    object oldArray = map->array();
    PROTECT(t, oldArray);

    unsigned newLength = nextPowerOfTwo(size);
    if (oldArray and arrayLength(t, oldArray) == newLength) {
      return;
    }

    newArray = reinterpret_cast<object>(makeArray(t, newLength));

    if (oldArray != map->array()) {
      // a resize was performed during a GC via the makeArray call
      // above; nothing left to do
      return;
    }

    if (oldArray) {
      bool weak = objectClass(t, map) == type(t, GcWeakHashMap::Type);
      for (unsigned i = 0; i < arrayLength(t, oldArray); ++i) {
        object next;
        for (object p = arrayBody(t, oldArray, i); p; p = next) {
          next = tripleThird(t, p);

          object k = tripleFirst(t, p);
          if (weak) {
            k = jreferenceTarget(t, k);
            if (k == 0) {
              continue;
            }
          }

          unsigned index = hash(t, k) & (newLength - 1);

          set(t, p, TripleThird, arrayBody(t, newArray, index));
          set(t, newArray, ArrayBody + (index * BytesPerWord), p);
        }
      }
    }
  }
  
  set(t, reinterpret_cast<object>(map), HashMapArray, newArray);
}

void
hashMapInsert(Thread* t, GcHashMap* map, object key, object value,
              uint32_t (*hash)(Thread*, object))
{
  // note that we reinitialize the array variable whenever an
  // allocation (and thus possibly a collection) occurs, in case the
  // array changes due to a table resize.

  PROTECT(t, map);

  uint32_t h = hash(t, key);

  bool weak = objectClass(t, map) == type(t, GcWeakHashMap::Type);

  object array = map->array();

  ++ map->size();

  if (array == 0 or map->size() >= arrayLength(t, array) * 2) { 
    PROTECT(t, key);
    PROTECT(t, value);

    hashMapResize(t, map, hash, array ? arrayLength(t, array) * 2 : 16);

    array = map->array();
  }

  object k = key;

  if (weak) {
    PROTECT(t, key);
    PROTECT(t, value);

    object r = reinterpret_cast<object>(makeWeakReference(t, 0, 0, 0, 0));
    jreferenceTarget(t, r) = key;
    jreferenceVmNext(t, r) = t->m->weakReferences;
    t->m->weakReferences = r;
    k = r;

    array = map->array();
  }

  object n = reinterpret_cast<object>(makeTriple(t, k, value, 0));

  array = map->array();

  unsigned index = h & (arrayLength(t, array) - 1);

  set(t, n, TripleThird, arrayBody(t, array, index));
  set(t, array, ArrayBody + (index * BytesPerWord), n);

  if (map->size() <= arrayLength(t, array) / 3) {
    // this might happen if nodes were removed during GC in which case
    // we weren't able to resize at the time
    hashMapResize(t, map, hash, arrayLength(t, array) / 2);
  }
}

object
hashMapRemoveNode(Thread* t, GcHashMap* map, unsigned index, object p, object n)
{
  if (p) {
    set(t, p, TripleThird, tripleThird(t, n));
  } else {
    set(t, map->array(), ArrayBody + (index * BytesPerWord),
        tripleThird(t, n));
  }
  -- map->size();
  return n;
}

object
hashMapRemove(Thread* t, GcHashMap* map, object key,
              uint32_t (*hash)(Thread*, object),
              bool (*equal)(Thread*, object, object))
{
  bool weak = objectClass(t, map) == type(t, GcWeakHashMap::Type);

  object array = map->array();
  object o = 0;
  if (array) {
    unsigned index = hash(t, key) & (arrayLength(t, array) - 1);
    object p = 0;
    for (object n = arrayBody(t, array, index); n;) {
      object k = tripleFirst(t, n);
      if (weak) {
        k = jreferenceTarget(t, k);
        if (k == 0) {
          n = tripleThird(t, hashMapRemoveNode(t, map, index, p, n));
          continue;
        }
      }

      if (equal(t, key, k)) {
        o = tripleSecond(t, hashMapRemoveNode(t, map, index, p, n));
        break;
      } else {
        p = n;
        n = tripleThird(t, n);
      }
    }

    if ((not t->m->collecting)
        and map->size() <= arrayLength(t, array) / 3)
    {
      PROTECT(t, o);
      hashMapResize(t, map, hash, arrayLength(t, array) / 2);
    }
  }

  return o;
}

void
listAppend(Thread* t, object list, object value)
{
  PROTECT(t, list);

  ++ listSize(t, list);
  
  object p = reinterpret_cast<object>(makePair(t, value, 0));
  if (listFront(t, list)) {
    set(t, listRear(t, list), PairSecond, p);
  } else {
    set(t, list, ListFront, p);
  }
  set(t, list, ListRear, p);
}

object
vectorAppend(Thread* t, object vector, object value)
{
  if (vectorLength(t, vector) == vectorSize(t, vector)) {
    PROTECT(t, vector);
    PROTECT(t, value);

    object newVector = reinterpret_cast<object>(makeVector
      (t, vectorSize(t, vector), max(16, vectorSize(t, vector) * 2)));

    if (vectorSize(t, vector)) {
      memcpy(&vectorBody(t, newVector, 0),
             &vectorBody(t, vector, 0),
             vectorSize(t, vector) * BytesPerWord);
    }

    vector = newVector;
  }

  set(t, vector, VectorBody + (vectorSize(t, vector) * BytesPerWord), value);
  ++ vectorSize(t, vector);
  return vector;
}

object
growArray(Thread* t, object array)
{
  PROTECT(t, array);

  object newArray = reinterpret_cast<object>(makeArray
    (t, array == 0 ? 16 : (arrayLength(t, array) * 2)));

  if (array) {
    memcpy(&arrayBody(t, newArray, 0), &arrayBody(t, array, 0),
           arrayLength(t, array));
  }

  return newArray;
}

object
treeQuery(Thread* t, object tree, intptr_t key, object sentinal,
          intptr_t (*compare)(Thread* t, intptr_t key, object b))
{
  object node = treeFind(t, tree, key, sentinal, compare);
  return (node ? getTreeNodeValue(t, node) : 0);
}

object
treeInsert(Thread* t, Zone* zone, object tree, intptr_t key, object value,
           object sentinal,
           intptr_t (*compare)(Thread* t, intptr_t key, object b))
{
  PROTECT(t, tree);
  PROTECT(t, sentinal);

  object node = reinterpret_cast<object>(makeTreeNode(t, value, sentinal, sentinal));

  TreeContext c(t, zone);
  treeFind(t, &c, tree, key, node, sentinal, compare);
  expect(t, c.fresh);

  return treeAdd(t, &c);
}

void
treeUpdate(Thread* t, object tree, intptr_t key, object value, object sentinal,
           intptr_t (*compare)(Thread* t, intptr_t key, object b))
{
  setTreeNodeValue(t, treeFind(t, tree, key, sentinal, compare), value);
}

} // namespace vm
