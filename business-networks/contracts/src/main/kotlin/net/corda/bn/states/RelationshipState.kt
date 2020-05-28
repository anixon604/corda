package net.corda.bn.states

import net.corda.bn.contracts.RelationshipContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

@BelongsToContract(RelationshipContract::class)
class RelationshipState<T>(
        val membershipId: UniqueIdentifier,
        val groups: Map<String, Group<T>>,
        override val participants: List<AbstractParty>
) : ContractState

data class Group<T>(val participants: List<Party>, val metadata: T)