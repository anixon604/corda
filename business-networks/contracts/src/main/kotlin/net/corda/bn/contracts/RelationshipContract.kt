package net.corda.bn.contracts

import net.corda.bn.states.RelationshipState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException

open class RelationshipContract : Contract {

    companion object {
        private const val CONTRACT_NAME = "net.corda.bn.contracts.RelationshipContract"
    }

    open class Commands : CommandData, TypeOnlyCommandData() {
        class Issue : Commands()
        class Amend : Commands()
        class Exit : Commands()
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val output = tx.outputs.single { it.data is RelationshipState<*> }
        val outputRelationship = output.data as RelationshipState<*>

        requireThat {
            "Output state has to be validated by ${contractName()}" using (output.contract == contractName())
            if (tx.inputStates.isNotEmpty()) {
                val input = tx.inputs.single()
                val inputState = input.state.data as RelationshipState<*>
                "Input state has to be validated by ${contractName()}" using (input.state.contract == contractName())
                "Input and output state should have same membership IDs" using (inputState.membershipId == outputRelationship.membershipId)
            }
        }

        when (command.value) {
            is Commands.Issue -> verifyIssue(tx, command, tx.outputsOfType<RelationshipState<*>>().single())
            is Commands.Amend -> verifyAmend(tx, command, tx.inputsOfType<RelationshipState<*>>().single(), tx.outputsOfType<RelationshipState<*>>().single())
            is Commands.Exit -> verifyExit(tx, command, tx.inputsOfType<RelationshipState<*>>().single())
            else -> throw IllegalArgumentException("Unsupported command ${command.value}")
        }
    }

    open fun contractName() = CONTRACT_NAME

    open fun verifyIssue(tx: LedgerTransaction, command: CommandWithParties<Commands>, outputRelationship: RelationshipState<*>) = requireThat { }

    open fun verifyAmend(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputRelationship: RelationshipState<*>,
            outputRelationship: RelationshipState<*>
    ) = requireThat { }

    open fun verifyExit(tx: LedgerTransaction, command: CommandWithParties<Commands>, inputRelationship: RelationshipState<*>) = requireThat { }
}