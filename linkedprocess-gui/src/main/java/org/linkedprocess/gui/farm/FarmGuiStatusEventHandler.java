package org.linkedprocess.gui.farm;

import org.linkedprocess.LinkedProcess;
import org.linkedprocess.farm.StatusEventHandler;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 * @version LoPSideD 0.1
 */
public class FarmGuiStatusEventHandler extends StatusEventHandler {

    protected FarmGui farmGui;


    public FarmGuiStatusEventHandler(FarmGui farmGui) {
        super(farmGui.getXmppFarm());
        this.farmGui = farmGui;
    }

    public void schedulerStatusChanged(LinkedProcess.FarmStatus status) {
        super.schedulerStatusChanged(status);
    }

    public void virtualMachineStatusChanged(String vmJid, LinkedProcess.VmStatus status) {
        super.virtualMachineStatusChanged(vmJid, status);
        farmGui.updateVirtualMachineTree(vmJid, status);
    }
}
