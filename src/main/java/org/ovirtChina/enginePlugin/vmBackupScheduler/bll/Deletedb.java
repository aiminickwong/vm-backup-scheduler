package org.ovirtChina.enginePlugin.vmBackupScheduler.bll;

import org.ovirt.engine.sdk.Api;

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

	private void findAndDelete() {
		// TODO Auto-generated method stub
		deletedb();
	}
}
