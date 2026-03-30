package com.example.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

@Mixin(HopperBlockEntity.class)
public class HopperMixin {
    
    @Inject(method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z", at = @At("HEAD"), cancellable = true)
    private static void fixInfHopperVoid(Container container, ItemEntity itemEntity, CallbackInfoReturnable<Boolean> cir) {
        
        if (container == null) return; 

        ItemStack itemStack = itemEntity.getItem();
        
        ItemStack toInsert = itemStack.copy();

        int maxInsert = toInsert.getMaxStackSize();

        if (toInsert.getCount() > maxInsert) {
            toInsert.setCount(maxInsert);
        }

        ItemStack remainder = HopperBlockEntity.addItem(null, container, toInsert, null);

        int actAccepted = maxInsert - remainder.getCount();

        if (actAccepted > 0) {
            itemStack.shrink(actAccepted);  
            itemEntity.setItem(itemStack);

            if (itemStack.isEmpty()) itemEntity.discard();

            cir.setReturnValue(true);
        } else {
            cir.setReturnValue(false);
        }

    }
}
