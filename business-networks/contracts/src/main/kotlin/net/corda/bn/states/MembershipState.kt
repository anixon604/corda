package net.corda.bn.states

import net.corda.bn.contracts.MembershipContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

@BelongsToContract(MembershipContract::class)
data class MembershipState<I, R>(
        val identity: Pair<Party, I>,
        val networkId: String,
        val status: MembershipStatus,
        val role: R,
        override val linearId: UniqueIdentifier = UniqueIdentifier(),
        override val participants: List<AbstractParty>
) : LinearState

enum class MembershipStatus { PENDING, ACTIVE, SUSPENDED, REVOKED }