package org.linkedprocess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.replayAll;

import java.util.ArrayList;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.junit.Before;
import org.junit.Test;
import org.linkedprocess.farm.Farm;
import org.linkedprocess.farm.ManageBindings;
import org.linkedprocess.farm.PingJob;
import org.linkedprocess.farm.SubmitJob;
import org.linkedprocess.farm.TerminateVm;
import org.linkedprocess.farm.os.Vm;
import org.linkedprocess.farm.os.VmBindings;
import org.linkedprocess.testing.offline.OfflineTest;

public class OfflineVmTest extends OfflineTest {

	private static final String PETER_HAR_SETT_MANGA_SNYGGA_FLICKOR = "Peter har sett manga snygga flickor";
	private static final String FIRST_NAME = "name";
	private static final String FULL_NAME = "full_name";
	private MockFarmXmppConnection connection;
	private ArrayList<Packet> sentPackets;
	private Farm farm;
	private Vm vm;

	@Before
	public void startVM() throws Exception {
		XMPPConnection farmConn = createMock(XMPPConnection.class);
		connection = new MockFarmXmppConnection(new ConnectionConfiguration(
				server, port), "LoPFarm", farmConn);
		OfflineTest.prepareMocksAndConnection(farmConn, connection);
		sentPackets = connection.sentPackets;
		replayAll();
		// start the farm
		farm = new Farm(server, port, username, password, null);
		vm = farm.spawnVm(CLIENT_JID, JAVASCRIPT);

	}

	@Test
	public void checkingStatusOnNonExistingJobShouldReturnError()
			throws Exception {

		// check without job id
		PingJob status = new PingJob();
		status.setVmId(vm.getVmId());
		connection.clearPackets();
		connection.pingJob.processPacket(status);
		connection.waitForResponse(1000);
		PingJob result = (PingJob) sentPackets.get(0);
		assertEquals(IQ.Type.ERROR, result.getType());
		assertTrue(result.toXML().contains(
				LinkedProcess.LopErrorType.MALFORMED_PACKET.toString()));

		// non-existent job
		status.setJobId("test");
		connection.clearPackets();
		connection.pingJob.processPacket(status);
		connection.waitForResponse(1000);
		result = (PingJob) sentPackets.get(0);
		assertEquals(IQ.Type.ERROR, result.getType());
		assertTrue(result.toXML().contains(
				LinkedProcess.LopErrorType.JOB_NOT_FOUND.toString()));

	}

	@Test
	public void sendingAnDifferentValidAndNonValidJobPackets()
			throws Exception {

		// send the eval packet, password missing
		SubmitJob eval = new SubmitJob();
		eval.setExpression("20 + 52;");
		eval.setTo(vm.getVmId());
		eval.setFrom(CLIENT_JID);
		eval.setVmId(vm.getVmId());
		connection.clearPackets();
		connection.submitJob.processPacket(eval);
		connection.waitForResponse(1000);
		// now, a new packet should have been sent back from the VM
		assertEquals(1, sentPackets.size());
		// sent packet should refer to the same pID
		SubmitJob result = (SubmitJob) sentPackets.get(0);
		// now, a new packet should have been sent back from the VM
		// sent packet should refer to the same pID
		assertEquals(IQ.Type.RESULT, result.getType());
		assertEquals("72", result.getExpression());

		// now, try with a wrong evaluation
		eval.setExpression("buh+2sdf;==");
		connection.clearPackets();
		connection.submitJob.processPacket(eval);
		connection.waitForResponse(1500);
		// sent packet should refer to the same pID
		result = (SubmitJob) sentPackets.get(0);
		assertEquals(IQ.Type.ERROR, result.getType());
		assertTrue(result.toXML().contains(
				LinkedProcess.LopErrorType.EVALUATION_ERROR.toString()));
	}

	@Test
	public void sendingATerminatePacketShouldCloseTheVM() throws Exception {
		connection.clearPackets();
		TerminateVm terminate = new TerminateVm();
		terminate.setVmId(vm.getVmId());
		terminate.setTo(vm.getVmId());
		connection.terminateVm.processPacket(terminate);

		assertEquals(1, sentPackets.size());
		// first one - IQ
		TerminateVm result = (TerminateVm) sentPackets.get(0);
		assertEquals(IQ.Type.RESULT, result.getType());
		assertEquals(0, farm.getVms().size());
	}

	@Test
	public void testManageBindings() throws Exception {
		connection.clearPackets();
		ManageBindings bindings = new ManageBindings();
		bindings.setVmId(vm.getVmId());
		bindings.setType(IQ.Type.SET);
		// bindings.setVmPassword(vm.getVmPassword());
		bindings.addBinding(FIRST_NAME, "Peter",
				VmBindings.XMLSchemaDatatype.STRING.getURI());
		bindings.addBinding(FULL_NAME, "", VmBindings.XMLSchemaDatatype.STRING
				.getURI());
		connection.manageBindings.processPacket(bindings);
		ManageBindings result = (ManageBindings) sentPackets.get(0);
		assertEquals(IQ.Type.RESULT, result.getType());
		// now, submit a job that changes the binding

		connection.clearPackets();
		SubmitJob job = new SubmitJob();
		job.setVmId(vm.getVmId());
		job.setFarmPassword(farm.getPassword());
		// job.setVmPassword(vm.getVmPassword());
		job
				.setExpression("full_name = name + ' har sett manga snygga flickor'");
		connection.submitJob.processPacket(job);
		connection.waitForResponse(2000);
		SubmitJob jobresult = (SubmitJob) connection.getLastPacket();
		assertEquals(PETER_HAR_SETT_MANGA_SNYGGA_FLICKOR, jobresult
				.getExpression());

		// now, the same should be bound to full_name now.
		connection.clearPackets();
		ManageBindings getResult = new ManageBindings();
		getResult.addBinding(FULL_NAME, "", VmBindings.XMLSchemaDatatype.STRING
				.getURI());
		getResult.setVmId(vm.getVmId());
		getResult.setType(IQ.Type.GET);
		connection.manageBindings.processPacket(getResult);
		// connection.waitForResponse(1000);
		ManageBindings resBinings = (ManageBindings) connection.getLastPacket();
		assertEquals(PETER_HAR_SETT_MANGA_SNYGGA_FLICKOR, resBinings
				.getBindings().getTyped(FULL_NAME).getValue());

	}

	@Test
	public void checkCorrectStartupStateOfVm() throws Exception {
		// now, a new packet should have been sent back from the VM
		assertEquals(1, sentPackets.size());
		// sent packet should refer to the same pID
		Presence result = (Presence) sentPackets.get(0);
		// assertEquals(result.getPacketID(), spawnPacketId);
		assertEquals(Presence.Type.available, result.getType());
		// now we should have 5 PacketListeners for the VM
		ArrayList<PacketListener> packetListeners = connection.packetListeners;
		assertEquals(7, packetListeners.size());
		assertNotNull(connection.submitJob);
		assertNotNull(connection.terminateVm);
		assertNotNull(connection.pingJob);
		assertNotNull(connection.manageBindings);
		assertNotNull(connection.abortJob);
		assertNotNull(connection.spawn);
		assertNotNull(connection.terminateVm);
		assertEquals(1, farm.getVms().size());

		// shut down
		connection.clearPackets();
		farm.terminateVm(farm.getVms().iterator().next().getVmId());
		assertEquals("No packets should be sent on shutdown", 0, sentPackets
				.size());
		assertEquals(0, farm.getVms().size());
	}
}
