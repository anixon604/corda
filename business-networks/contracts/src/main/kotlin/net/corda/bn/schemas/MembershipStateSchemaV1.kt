package net.corda.bn.schemas

import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import javax.persistence.Entity
import javax.persistence.Table

object MembershipStateSchemaV1 : MappedSchema(schemaFamily = MembershipState::class.java, version = 1, mappedTypes = listOf(PersistentMembershipState::class.java)) {
    @Entity
    @Table(name = "membership_state")
    class PersistentMembershipState(val cordaIdentity: Party, val networkId: String, val status: MembershipStatus)
}