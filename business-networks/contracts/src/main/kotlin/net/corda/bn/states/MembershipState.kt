package net.corda.bn.states

import net.corda.bn.contracts.MembershipContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

@BelongsToContract(MembershipContract::class)
data class MembershipState<out I : Any, out R : Any>(
        val identity: MembershipIdentity<I>,
        val networkId: String,
        val status: MembershipStatus,
        val role: R? = null,
        override val linearId: UniqueIdentifier = UniqueIdentifier(),
        override val participants: List<AbstractParty>
) : LinearState {
    fun isPending() = status == MembershipStatus.PENDING
    fun isActive() = status == MembershipStatus.ACTIVE
    fun isSuspended() = status == MembershipStatus.SUSPENDED
    fun isRevoked() = status == MembershipStatus.REVOKED
}

data class MembershipIdentity<out I : Any>(val cordaIdentity: Party, val additionalIdentity: I? = null)

enum class MembershipStatus { PENDING, ACTIVE, SUSPENDED, REVOKED }