package com.min.edu.admission.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdmissionQrImageGeneratorTest {

    @Test
    void generate_returnsPngBytes() {
        AdmissionQrImageGenerator generator = new AdmissionQrImageGenerator();

        byte[] image = generator.generate("qr-token");

        assertThat(image).isNotEmpty();
        assertThat(image[0]).isEqualTo((byte) 0x89);
        assertThat(image[1]).isEqualTo((byte) 0x50);
        assertThat(image[2]).isEqualTo((byte) 0x4E);
        assertThat(image[3]).isEqualTo((byte) 0x47);
    }
}
