package net.corda.bn.flows

import net.corda.bn.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken

@CordaService
class DatabaseService(val serviceHub: ServiceHub) : SingletonSerializeAsToken() {

    fun getMembership(party: Party, networkId: String): MembershipState<*, *> {
        TODO()
    }

    fun getMembership(linearId: UniqueIdentifier): StateAndRef<MembershipState<*, *>> {
        TODO()
    }

    fun getMembersAuthorisedToActivateMembership(networkId: String): List<Party> = emptyList()

    fun getMembersAuthorisedToModifyMembershipStatus(networkId: String): List<Party> = emptyList()

    fun getMembersAuthorisedToModifyMembership(networkId: String): List<Party> = emptyList()

    fun createPendingMembershipRequest(party: Party) {}
}