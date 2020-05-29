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
        val input = if (tx.inputStates.isNotEmpty()) tx.inputs.single() else null
        val inputState = input?.state?.data as? RelationshipState<*>
        val output = if (tx.outputStates.isNotEmpty()) tx.outputs.single() else null
        val outputState = output?.data as? RelationshipState<*>

        requireThat {
            input?.apply {
                "Input state has to be validated by ${contractName()}" using (state.contract == contractName())
                "Input state must have at least one group" using (inputState!!.groups.isNotEmpty())
            }
            output?.apply {
                "Output state has to be validated by ${contractName()}" using (contract == contractName())
                "Output state must have at least one group" using (outputState!!.groups.isNotEmpty())
            }
            if (inputState != null && outputState != null) {
                "Input and output state should have same membership IDs" using (inputState.membershipId == outputState.membershipId)
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

    open fun verifyIssue(tx: LedgerTransaction, command: CommandWithParties<Commands>, outputRelationship: RelationshipState<*>) = requireThat {
        "Relationship issuance transaction shouldn't contain any inputs" using (tx.inputs.isEmpty())
    }

    open fun verifyAmend(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputRelationship: RelationshipState<*>,
            outputRelationship: RelationshipState<*>
    ) = requireThat {
        "Input and output states of amendment transaction should have different groups field" using (inputRelationship.groups != outputRelationship.groups)
        "Input and output state's group metadata should be of the same type" using (
                inputRelationship.groups.values.first().metadata.javaClass == outputRelationship.groups.values.first().metadata.javaClass)
    }

    open fun verifyExit(tx: LedgerTransaction, command: CommandWithParties<Commands>, inputRelationship: RelationshipState<*>) = requireThat {
        "Relationship exit transaction shouldn't contain any outputs" using (tx.outputs.isEmpty())
    }
}