package com.mk2525.vsfluidlink.compat.tfmg;

import com.drmangotea.tfmg.content.electricity.base.ElectricalNetwork;
import com.drmangotea.tfmg.content.electricity.utilities.electric_motor.ElectricMotorBlockEntity;

/** Native solver with deferred motor output reconciliation after the complete power calculation. */
final class TfmgElectricalNetwork extends ElectricalNetwork {
    TfmgElectricalNetwork(long id) { super(id); }

    @Override public void updateNetwork() {
        super.updateNetwork();
        for (var member : members) {
            if (member instanceof ElectricMotorBlockEntity motor && !motor.isRemoved()
                    && !motor.getLevelAccessor().isClientSide()
                    && motor.getGeneratedSpeed() != motor.getTheoreticalSpeed()) {
                // Reconcile kinetic output only after the native solver has settled the entire network.
                motor.delayedUpdate = true;
            }
        }
    }
}
