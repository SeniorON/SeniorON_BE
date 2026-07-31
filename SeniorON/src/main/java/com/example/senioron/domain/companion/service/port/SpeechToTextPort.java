package com.example.senioron.domain.companion.service.port;

import com.example.senioron.domain.companion.service.model.TranscriptionResult;
import com.example.senioron.domain.companion.service.model.VoiceAudio;

public interface SpeechToTextPort {

    TranscriptionResult transcribe(VoiceAudio audio);
}
