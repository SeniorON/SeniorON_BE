package com.example.senioron.domain.companion.service.port;

import com.example.senioron.domain.companion.service.model.SynthesizedAudio;

public interface SpeechSynthesisPort {

    SynthesizedAudio synthesize(String text);
}