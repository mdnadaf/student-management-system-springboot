package com.cwm.studentmanagement.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.cwm.studentmanagement.model.Users;
import com.cwm.studentmanagement.repository.UsersRepository;

@Configuration
public class DataIntializer {

	private static final Logger log = LoggerFactory.getLogger(DataIntializer.class);

	@Bean
	CommandLineRunner loadSampleData(
			UsersRepository usersRepository,
			PasswordEncoder passwordEncoder,
			@Value("${app.admin.username:Admin}") String adminUsername,
			@Value("${app.admin.password:admin@123}") String adminPassword) {

		return args -> {
			if (usersRepository.existsByUsername(adminUsername)) {
				log.info("Default admin user '{}' already exists; skipping seed.", adminUsername);
				return;
			}
			try {
				Users user = new Users();
				user.setUsername(adminUsername);
				user.setPassword(passwordEncoder.encode(adminPassword));
				user.setActive(true);
				usersRepository.save(user);
				log.info("Seeded default admin user '{}'.", adminUsername);
			} catch (DataIntegrityViolationException ex) {
				// Safe fallback if another instance created the user concurrently during a rolling deploy
				log.info("Default admin user '{}' already exists (concurrent seed); continuing startup.",
						adminUsername);
			}
		};
	}

}
