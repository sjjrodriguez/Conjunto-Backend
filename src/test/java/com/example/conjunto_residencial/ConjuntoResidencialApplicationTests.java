package com.example.conjunto_residencial;

import com.residencial.ResidencialApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// El test está en otro paquete que la aplicación: hay que indicarle la clase principal
@SpringBootTest(classes = ResidencialApplication.class)
class ConjuntoResidencialApplicationTests {

	@Test
	void contextLoads() {
	}

}
