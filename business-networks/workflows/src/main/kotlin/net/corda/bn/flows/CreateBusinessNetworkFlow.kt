package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.MembershipContract
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.*

@InitiatingFlow
@StartableByRPC
class CreateBusinessNetworkFlow : FlowLogic<SignedTransaction>() {

    private fun createMembershipRequest(): SignedTransaction {
        val membership = MembershipState(
                identity = ourIdentity,
                networkId = UUID.randomUUID().toString(),
                status = MembershipStatus.PENDING,
                participants = listOf(ourIdentity)
        )

        val builder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addOutputState(membership)
                .addCommand(MembershipContract.Commands.Request(), ourIdentity.owningKey)
        builder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(stx, emptyList()))
    }

    @Suspendable
    override fun call(): SignedTransaction {
        val membership = createMembershipRequest().tx.outRefsOfType(MembershipState::class.java).single()

        val builder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(membership)
                .addOutputState(membership.state.data.copy(status = MembershipStatus.ACTIVE))
                .addCommand(MembershipContract.Commands.Activate(), ourIdentity.owningKey)
        builder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(stx, emptyList()))
    }
}