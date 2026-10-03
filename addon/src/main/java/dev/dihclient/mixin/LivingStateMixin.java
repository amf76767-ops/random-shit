package dev.dihclient.mixin;

import dev.dihclient.emote.EmotePose;
import dev.dihclient.glue.EmoteHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives the render state of living entities a place for the emote pose. */
@Mixin(targets = "net.minecraft.class_10042")
public abstract class LivingStateMixin implements EmoteHolder {
    @Unique
    private EmotePose dih$pose;

    @Override
    public EmotePose dih$getPose() {
        return this.dih$pose;
    }

    @Override
    public void dih$setPose(EmotePose pose) {
        this.dih$pose = pose;
    }
}
