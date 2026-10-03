package dev.dihclient.glue;

import dev.dihclient.emote.EmotePose;

/** Added to the render state of a living entity by LivingStateMixin: carries the emote pose from extraction to drawing. */
public interface EmoteHolder {
    EmotePose dih$getPose();

    void dih$setPose(EmotePose pose);
}
