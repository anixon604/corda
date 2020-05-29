package net.corda.bn.contracts

import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException

open class MembershipContract : Contract {

    companion object {
        private const val CONTRACT_NAME = "net.corda.bn.contracts.MembershipContract"
    }

    open class Commands : CommandData, TypeOnlyCommandData() {
        class Request : Commands()
        class Activate : Commands()
        class Suspend : Commands()
        class Revoke : Commands()
        class AmendIdentity : Commands()
        class ModifyRole : Commands()
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val output = tx.outputs.single()
        val outputState = output.data as MembershipState<*, *>

        requireThat {
            "Output state has to be validated by ${contractName()}" using (output.contract == contractName())
            if (tx.inputStates.isNotEmpty()) {
                val input = tx.inputs.single()
                val inputState = input.state.data as MembershipState<*, *>
                "Input state has to be validated by ${contractName()}" using (input.state.contract == contractName())
                "Input and output state should have same Corda identity" using (inputState.identity.cordaIdentity == outputState.identity.cordaIdentity)
                "Input and output state should have same network IDs" using (inputState.networkId == outputState.networkId)
                "Input and output state should have same linear IDs" using (inputState.linearId == outputState.linearId)
            }
        }

        when (command.value) {
            is Commands.Request -> verifyRequest(tx, command, outputState)
            is Commands.Activate -> verifyActivate(tx, command, tx.inputsOfType<MembershipState<*, *>>().single(), outputState)
            is Commands.Suspend -> verifySuspend(tx, command, tx.inputsOfType<MembershipState<*, *>>().single(), outputState)
            is Commands.Revoke -> verifyRevoke(tx, command, tx.inputsOfType<MembershipState<*, *>>().single(), outputState)
            is Commands.AmendIdentity -> verifyAmendIdentity(tx, command, tx.inputsOfType<MembershipState<*, *>>().single(), outputState)
            is Commands.ModifyRole -> verifyModifyRole(tx, command, tx.inputsOfType<MembershipState<*, *>>().single(), outputState)
            else -> throw IllegalArgumentException("Unsupported command ${command.value}")
        }
    }

    open fun contractName() = CONTRACT_NAME

    open fun verifyRequest(tx: LedgerTransaction, command: CommandWithParties<Commands>, outputMembership: MembershipState<*, *>) = requireThat {
        "Membership request transaction shouldn't contain any inputs" using (tx.inputs.isEmpty())
        "Membership request transaction should contain output state in PENDING status" using (outputMembership.isPending())
    }

    open fun verifyActivate(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputMembership: MembershipState<*, *>,
            outputMembership: MembershipState<*, *>
    ) = requireThat {
        "Input state of membership activation transaction shouldn't be already active" using (!inputMembership.isActive())
        "Input state of membership activation transaction shouldn't be revoked" using (!inputMembership.isRevoked())
        "Output state of membership activation transaction should be active" using (outputMembership.isActive())
        "Input and output state of membership activation transaction should only have different status field" using (
                inputMembership.copy(status = MembershipStatus.ACTIVE) == outputMembership)
    }

    open fun verifySuspend(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputMembership: MembershipState<*, *>,
            outputMembership: MembershipState<*, *>
    ) = requireThat {
        "Input state of membership suspension transaction shouldn't be already suspended" using (!inputMembership.isSuspended())
        "Input state of membership suspension transaction shouldn't be revoked" using (!inputMembership.isRevoked())
        "Output state of membership suspension transaction should be suspended" using (outputMembership.isSuspended())
        "Input and output state of membership suspension transaction should only have different status field" using (
                inputMembership.copy(status = MembershipStatus.SUSPENDED) == outputMembership)
    }

    open fun verifyRevoke(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputMembership: MembershipState<*, *>,
            outputMembership: MembershipState<*, *>
    ) = requireThat {
        "Input state of membership revocation transaction shouldn't be already revoked" using (!inputMembership.isRevoked())
        "Output state of membership revocation transaction should be revoked" using (outputMembership.isRevoked())
        "Input and output state of membership revocation transaction should only have different status field" using (
                inputMembership.copy(status = MembershipStatus.REVOKED) == outputMembership)
    }

    open fun verifyAmendIdentity(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputMembership: MembershipState<*, *>,
            outputMembership: MembershipState<*, *>
    ) = requireThat {
        "Both input and output state of membership amendment transaction should be active" using (inputMembership.isActive() && outputMembership.isActive())
        "Input and output state of membership identity amendment transaction should have different additional identity" using (
                inputMembership.identity.additionalIdentity != outputMembership.identity.additionalIdentity)
        "Input and output state's additional identity field should be of same type" using (
                inputMembership.identity.additionalIdentity.javaClass == outputMembership.identity.additionalIdentity.javaClass)
        "Input and output state of membership identity amendment transaction should only have different identity field" using (
                inputMembership.copy(identity = outputMembership.identity) == outputMembership)
    }

    open fun verifyModifyRole(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputMembership: MembershipState<*, *>,
            outputMembership: MembershipState<*, *>
    ) = requireThat {
        "Both input and output state of role modification transaction should be active" using (inputMembership.isActive() && outputMembership.isActive())
        "Input and output state of role modification transaction should habe different role" using (inputMembership.role != outputMembership.role)
        "Input and output state's role field should be of same type" using (inputMembership.role.javaClass == outputMembership.role.javaClass)
        "Input and output state of role modification amendment transaction should only have different role field" using (
                inputMembership.copy(role = outputMembership.role) == outputMembership)
    }
}