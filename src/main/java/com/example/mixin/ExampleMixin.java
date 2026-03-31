package com.example.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.player.Player;

@Mixin(ItemEntity.class)
public abstract class ExampleMixin {

    @Shadow private int pickupDelay;

    @Inject(method = "tick", at = @At("TAIL"))
    private void updateItemLabelAndMerge(CallbackInfo ci) {
        ItemEntity entity = (ItemEntity) (Object) this;
        if (entity.level().isClientSide()) return;

        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) return;

        Component label = Component.literal(stack.getCount() + "x ").append(stack.getHoverName());
        if (!label.equals(entity.getCustomName())) {
            entity.setCustomName(label);
            entity.setCustomNameVisible(true);
        }

        var nearbyItems = entity.level().getEntitiesOfClass(ItemEntity.class, 
                entity.getBoundingBox().inflate(2.0D), 
                (other) -> other != entity && other.isAlive());

        for (ItemEntity otherItem : nearbyItems) {
            if (otherItem.getItem().getItem() == stack.getItem()) {
                if (otherItem.tickCount < entity.tickCount) {
                    this.mergeInfinite(entity, otherItem);
                    break; 
                }
            }
        }
    }

    private void mergeInfinite(ItemEntity source, ItemEntity target) {
        ItemStack sourceStack = source.getItem();
        ItemStack targetStack = target.getItem();

        if (targetStack.getMaxStackSize() > 1 && ItemStack.isSameItemSameComponents(sourceStack, targetStack)) {
            
            int newCount = targetStack.getCount() + sourceStack.getCount();
            
            targetStack.setCount(newCount);
            target.setItem(targetStack);   

            source.discard();
        }
    }

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void fastPickupWithDelay(Player player, CallbackInfo ci) {
        ItemEntity entity = (ItemEntity) (Object) this;
        
        if (entity.level().isClientSide() || entity.isRemoved() || this.pickupDelay > 0) return;

        ItemStack itemInEntity = entity.getItem();

        if (itemInEntity.isEmpty()) return;

        if (player.getInventory().getFreeSlot() != -1 || hasSpaceInStack(player, itemInEntity)) {
            
            int pickupAmount = 64; 
            int currentCount = itemInEntity.getCount();
            int toTransfer = Math.min(currentCount, pickupAmount);

            ItemStack toAdd = itemInEntity.copy();
            toAdd.setCount(toTransfer);

            int countBeforeAdd = toAdd.getCount();

            player.getInventory().add(toAdd);
            
            int actuallyTransferred = countBeforeAdd - toAdd.getCount();

            if (actuallyTransferred > 0) {
                itemInEntity.shrink(actuallyTransferred);
                
                if (itemInEntity.isEmpty()) {
                    entity.discard();
                } else {
                    entity.setItem(itemInEntity);
                }   
                ci.cancel();
            } 
        }
    }

    private boolean hasSpaceInStack(Player player, @NonNull ItemStack item) { 
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            
            if (slot != null && ItemStack.isSameItemSameComponents(slot, item)) {
                if (slot.getCount() < slot.getMaxStackSize()) {
                    return true;
                }
            }
        }
        return false;
    }

}