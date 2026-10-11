package com.maxonaddon.client.render;

import com.maxonaddon.client.model.GasCisternaItemModel;
import com.maxonaddon.item.GasCisternaItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class GasCisternaItemRenderer extends GeoItemRenderer<GasCisternaItem> {
    public GasCisternaItemRenderer() {
        super(new GasCisternaItemModel());
        this.withScale(0.2f); // Уменьшаем размер в руках
    }
}