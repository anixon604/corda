package net.corda.bn.contracts

import net.corda.bn.states.MembershipState
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
        val output = tx.outputs.single { it.data is MembershipState<*, *> }
        val outputMembership = output.data as MembershipState<*, *>

        requireThat {
            "Output state has to be validated by ${contractName()}" using (output.contract == contractName())
            if (tx.inputStates.isNotEmpty()) {
                val input = tx.inputs.single()
                val inputState = input.state.data as MembershipState<*, *>
                "Input state has to be validated by ${contractName()}" using (input.state.contract == contractName())
                "Input and output state should have same linear IDs" using (inputState.linearId == outputMembership.linearId)
            }
        }

        when (command.value) {
            is Commands.Request -> verifyRequest(tx, command, outputMembership)
            is Commands.Activate -> verifyActivate(tx, command, outputMembership)
            is Commands.Suspend -> verifySuspend(tx, command, outputMembership)
            is Commands.Revoke -> verifyRevoke(tx, command, outputMembership)
            is Commands.AmendIdentity -> verifyAmendIdentity(tx, command, outputMembership)
            is Commands.ModifyRole -> verifyModifyRole(tx, command, outputMembership)
            else -> throw IllegalArgumentException("Unsupported command ${command.value}")
        }
    }

    open fun contractName() = CONTRACT_NAME

    open fun verifyRequest(tx: LedgerTransaction, command: CommandWithParties<Commands>, outputMembership: MembershipState<*, *>) = requireThat { }

    open fun verifyActivate(tx: LedgerTransaction, command: CommandWithParties<Commands>, outputMembership: MembershipState<*, *>) = requireThat { }

    open fun verifySuspend(tx: LedgerTransaction, command: CommandWithParties<Commands>, outputMembership: MembershipState<*, *>) = requireThat { }

    open fun verifyRevoke(tx: LedgerTransaction, command: CommandWithParties<Commands>, outputMembership: MembershipState<*, *>) = requireThat { }

    open fun verifyAmendIdentity(tx: LedgerTransaction, command: CommandWithParties<Commands>, outputMembership: MembershipState<*, *>) = requireThat { }

    open fun verifyModifyRole(tx: LedgerTransaction, command: CommandWithParties<Commands>, outputMembership: MembershipState<*, *>) = requireThat { }
}