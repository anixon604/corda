package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.MembershipContract
import net.corda.bn.states.MembershipIdentity
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

@InitiatingFlow
@StartableByRPC
class RequestMembershipFlow(val authorisedParty: Party, val networkId: String) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        // send request to authorised member
        val authorisedPartySession = initiateFlow(authorisedParty)
        authorisedPartySession.send(networkId)

        // sign transaction
        val signResponder = object : SignTransactionFlow(authorisedPartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val command = stx.tx.commands.single()
                if (command.value !is MembershipContract.Commands.Request) {
                    throw FlowException("Only Request command is allowed")
                }

                val membershipState = stx.tx.outputs.single().data as MembershipState<*, *>
                if (ourIdentity != membershipState.identity.cordaIdentity) {
                    throw IllegalArgumentException("We have to be the member")
                }

                stx.toLedgerTransaction(serviceHub, false).verify()
            }
        }
        val stx = subFlow(signResponder)

        // receive finality flow
        return subFlow(ReceiveFinalityFlow(authorisedPartySession, stx.id))
    }
}

@InitiatingFlow
@InitiatedBy(RequestMembershipFlow::class)
class RequestMembershipFlowResponder(val session: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val networkId = session.receive<String>().unwrap { it }
        val counterparty = session.counterparty
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        if (databaseService.getMembership(networkId, counterparty) != null) {
            throw FlowException("Membership already exists")
        }

        // building transaction
        val membershipState = MembershipState<Any, Any>(
                identity = MembershipIdentity(counterparty, null),
                networkId = networkId,
                status = MembershipStatus.PENDING,
                participants = listOf()
        )
        val builder = TransactionBuilder()
                .addOutputState(membershipState)
                .addCommand(MembershipContract.Commands.Request(), counterparty.owningKey)
        builder.verify(serviceHub)

        // signing transaction
        val selfSignedTransaction = serviceHub.signInitialTransaction(builder)
        val allSignedTransaction = subFlow(CollectSignaturesFlow(selfSignedTransaction, listOf(session)))

        // finalise transaction
        val observerSessions = databaseService.getMembersAuthorisedToModifyMembershipStatus(networkId).map { initiateFlow(it) }.toSet()
        subFlow(FinalityFlow(allSignedTransaction, observerSessions + session))
    }
}

@InitiatedBy(RequestMembershipFlowResponder::class)
class RequestMembershipObserverFlow(val session: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        subFlow(ReceiveFinalityFlow(session))
    }
}