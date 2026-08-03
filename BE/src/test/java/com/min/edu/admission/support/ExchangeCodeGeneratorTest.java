package com.min.edu.admission.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.min.edu.admission.repository.ExchangeCodeRepository;

class ExchangeCodeGeneratorTest {

    @Test
    void generate_returnsThreeGroupsOfSixHexCharacters() {
        ExchangeCodeRepository exchangeCodeRepository = mock(ExchangeCodeRepository.class);
        given(exchangeCodeRepository.existsByCode(anyString())).willReturn(false);
        ExchangeCodeGenerator exchangeCodeGenerator =
            new ExchangeCodeGenerator(exchangeCodeRepository);

        String code = exchangeCodeGenerator.generate();

        assertThat(code).matches("^[A-F0-9]{6}-[A-F0-9]{6}-[A-F0-9]{6}$");
    }
}
