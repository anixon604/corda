package net.corda.bn.flows

import net.corda.bn.states.MembershipState
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test

class FlowTest {

    private val mockNetwork = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("net.corda.bn.contracts"),
            TestCordapp.findCordapp("net.corda.bn.flows")
    )))

    private val bno = mockNetwork.createNode(CordaX500Name("BNO", "BNO", "London", "GB"))
    private val member = mockNetwork.createNode(CordaX500Name("Member1", "London", "GB"))

//    init {
//        listOf(bno, member).forEach {
//            it.registerInitiatedFlow(RequestMembershipFlowResponder::class.java)
//        }
//    }

    @Before
    fun setup() = mockNetwork.runNetwork()

    @After
    fun tearDown() = mockNetwork.stopNodes()

    @Test
    fun `test`() {
        val stxFuture = bno.startFlow(CreateBusinessNetworkFlow())
        mockNetwork.runNetwork()
        val stx = stxFuture.getOrThrow()

        val bnoMembership = stx.tx.outputStates.single() as MembershipState
        val future = member.startFlow(RequestMembershipFlow(bno.info.chooseIdentityAndCert().party, bnoMembership.networkId))
        mockNetwork.runNetwork()
        val tx = future.getOrThrow()
        tx
    }
}