package dev.dihclient.glue;

import dev.dihclient.emote.EmotePose;

public interface EmoteHolder {
    EmotePose dih$getPose();

    void dih$setPose(EmotePose pose);
}
