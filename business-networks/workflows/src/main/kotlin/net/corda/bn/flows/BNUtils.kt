package net.corda.bn.flows

import net.corda.bn.flows.extensions.PublicCentralisedBNMemberAuth

object BNUtils {
    fun loadBNMemberAuth() = PublicCentralisedBNMemberAuth()
}