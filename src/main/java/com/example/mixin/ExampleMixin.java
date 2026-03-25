package com.example.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

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

        // 1. อัปเดตชื่อ Real-time
        Component label = Component.literal(stack.getCount() + "x ").append(stack.getHoverName());
        if (!label.equals(entity.getCustomName())) {
            entity.setCustomName(label);
            entity.setCustomNameVisible(true);
        }

        // 2. Logic การ Merge (ใช้โค้ดเดิมจากด้านบนได้เลย)
        // ... (ส่วน mergeInfinite ที่เราเขียนกันไปก่อนหน้านี้)
        var nearbyItems = entity.level().getEntitiesOfClass(ItemEntity.class, 
                entity.getBoundingBox().inflate(5.0D), 
                (other) -> other != entity && other.isAlive());

        for (ItemEntity otherItem : nearbyItems) {
            if (otherItem.getItem().getItem() == stack.getItem()) {
                // ถ้าเรา 'เก่ากว่า' ให้เราย้ายไปรวมกับตัวที่ 'ใหม่กว่า' (tickCount น้อยกว่า)
                if (otherItem.tickCount < entity.tickCount) {
                    this.mergeInfinite(entity, otherItem);
                    break; 
                }
            }
        }
    }

    // Helper method สำหรับการรวมไอเทม
    private void mergeInfinite(ItemEntity source, ItemEntity target) {
        ItemStack sourceStack = source.getItem();
        ItemStack targetStack = target.getItem();

        // 1. เช็คก่อนว่าไอเทมนี้อนุญาตให้ Stack ได้หรือไม่ (อาวุธ/ชุดเกราะจะมีค่านี้เป็น 1)
        // และต้องเป็นไอเทมชนิดเดียวกันเป๊ะๆ
        if (targetStack.getMaxStackSize() > 1 && ItemStack.isSameItemSameComponents(sourceStack, targetStack)) {
            
            // 2. รวมจำนวนทั้งหมดเข้าด้วยกัน
            int newCount = targetStack.getCount() + sourceStack.getCount();
            
            // 3. ตั้งค่าจำนวนใหม่ให้ตัวเป้าหมาย
            targetStack.setCount(newCount);
            target.setItem(targetStack);   

            // 5. ลบตัวเก่าทิ้ง
            source.discard();
        }
    }

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void fastPickupWithDelay(Player player, CallbackInfo ci) {
        ItemEntity entity = (ItemEntity) (Object) this;
        
        // 1. เช็คเบื้องต้น (รวมถึงเช็ค pickupDelay ผ่าน Shadow field)
        if (entity.level().isClientSide() || entity.isRemoved() || this.pickupDelay > 0) return;

        ItemStack itemInEntity = entity.getItem();
        if (itemInEntity.isEmpty()) return;

        // 2. เช็คก่อนว่าในกระเป๋ามีที่ว่างสำหรับไอเทมชนิดนี้อย่างน้อย 1 ชิ้นไหม
        if (player.getInventory().getFreeSlot() != -1 || hasSpaceInStack(player, itemInEntity)) {
            
            int pickupAmount = 64; 
            int currentCount = itemInEntity.getCount();
            int toTransfer = Math.min(currentCount, pickupAmount);

            ItemStack toAdd = itemInEntity.copy();
            toAdd.setCount(toTransfer);

            // 3. พยายามเพิ่มไอเทมเข้าตัว
            // บันทึกจำนวนก่อนเพิ่มไว้เทียบ
            int countBeforeAdd = toAdd.getCount();
            
            if (player.getInventory().add(toAdd)) {
                // คำนวณว่าเพิ่มเข้าไปได้จริงเท่าไหร่ (countBeforeAdd - จำนวนที่เหลือใน toAdd)
                int actuallyTransferred = countBeforeAdd - toAdd.getCount();

                // 4. ถ้าเก็บเข้าตัวได้จริง (มากกว่า 0) ถึงจะไปหักจากกองที่พื้น
                if (actuallyTransferred > 0) {
                    itemInEntity.shrink(actuallyTransferred);
                    
                    if (itemInEntity.isEmpty()) {
                        entity.discard();
                    } else {
                        entity.setItem(itemInEntity);
                    }
                    
                    // ป้องกัน Logic ปกติของเกมมาแทรกแซง
                    ci.cancel();
                } 
                // ถ้า actuallyTransferred เป็น 0 (คือกระเป๋าเต็มพอดีในจังหวะนั้น) 
                // โค้ดจะไม่ทำอะไรต่อ ไอเทมก็จะค้างอยู่ที่พื้นตามปกติ
            }
        }
    }

    private boolean hasSpaceInStack(Player player, ItemStack item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(slot, item) && slot.getCount() < slot.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

}