package net.corda.bn.flows

import net.corda.core.identity.Party

interface MembershipManagementFlow {
    fun requiredSigners(): List<Party>
}