package org.ovirtChina.enginePlugin.vmBackupScheduler.bll;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.http.client.ClientProtocolException;
import org.ovirt.engine.sdk.Api;
import org.ovirt.engine.sdk.decorators.VM;
import org.ovirt.engine.sdk.exceptions.ServerException;
import org.ovirtChina.enginePlugin.vmBackupScheduler.common.VmPolicy;
import org.ovirtChina.enginePlugin.vmBackupScheduler.dao.DbFacade;

public class Deletedb extends TimerSDKTask  {

    public Deletedb(Api api) {
        super(api);
    }

    @Override
    protected void peformAction() throws Exception {
        if (api != null) {
            findAndDelete();
        }
    }

	private void findAndDelete() throws ClientProtocolException, ServerException, IOException {
	    List<String> vmIDFromEngine = new ArrayList<String>();
        for(VM vm : api.getVMs().list()) {
            vmIDFromEngine.add(vm.getId());
        }
        Set<String> vmIDFromPolicy = new HashSet<String>();
        for(VmPolicy policy : DbFacade.getInstance().getVmPolicyDAO().selectallid()) {
            vmIDFromPolicy.add(policy.getVmID().toString());
        }
        vmIDFromPolicy.removeAll(vmIDFromEngine);
        for(String id : vmIDFromPolicy){
            DbFacade.getInstance().getVmPolicyDAO().deleteVmPolicy(UUID.fromString(id));
        }
	}
}
