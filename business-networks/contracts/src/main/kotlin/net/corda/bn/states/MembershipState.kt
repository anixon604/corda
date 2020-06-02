package net.corda.bn.states

import net.corda.bn.contracts.MembershipContract
import net.corda.bn.schemas.MembershipStateSchemaV1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.QueryableState
import java.time.Instant

@BelongsToContract(MembershipContract::class)
data class MembershipState<out I : Any, out R : Any>(
        val identity: MembershipIdentity<I>,
        val networkId: String,
        val status: MembershipStatus,
        val role: R? = null,
        val issued: Instant = Instant.now(),
        val modified: Instant = issued,
        override val linearId: UniqueIdentifier = UniqueIdentifier(),
        override val participants: List<AbstractParty>
) : LinearState, QueryableState {

    override fun generateMappedObject(schema: MappedSchema) = when (schema) {
        is MembershipStateSchemaV1 -> MembershipStateSchemaV1.PersistentMembershipState(
                cordaIdentity = identity.cordaIdentity,
                networkId = networkId,
                status = status
        )
        else -> throw IllegalArgumentException("Unrecognised schema $schema")
    }

    override fun supportedSchemas() = listOf(MembershipStateSchemaV1)

    fun isPending() = status == MembershipStatus.PENDING
    fun isActive() = status == MembershipStatus.ACTIVE
    fun isSuspended() = status == MembershipStatus.SUSPENDED
    fun isRevoked() = status == MembershipStatus.REVOKED
}

data class MembershipIdentity<out I : Any>(val cordaIdentity: Party, val additionalIdentity: I? = null)

enum class MembershipStatus { PENDING, ACTIVE, SUSPENDED, REVOKED }