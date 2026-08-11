package com.scrim.lolscrim.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.beans.Introspector;
import java.lang.annotation.Annotation;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

class DomainCollisionTest {

	private static final String BASE_PACKAGE = "com.scrim.lolscrim";

	@Test
	void entityNamesAndTablesAreUnique() {
		Set<Class<?>> entities = annotatedTypes(Entity.class);

		assertThat(entities)
				.extracting(Class::getSimpleName)
				.doesNotHaveDuplicates();
		assertThat(entities)
				.extracting(type -> type.getAnnotation(Table.class))
				.filteredOn(table -> table != null && !table.name().isBlank())
				.extracting(Table::name)
				.doesNotHaveDuplicates();
	}

	@Test
	void defaultSpringComponentNamesAreUnique() {
		assertThat(annotatedTypes(Component.class))
				.extracting(type -> Introspector.decapitalize(type.getSimpleName()))
				.doesNotHaveDuplicates();
	}

	@Test
	void supersededDomainMappingsStayRemoved() {
		assertThatThrownBy(() -> Class.forName("com.scrim.lolscrim.domain.group.Room"))
				.isInstanceOf(ClassNotFoundException.class);
		assertThatThrownBy(() -> Class.forName("com.scrim.lolscrim.domain.session.Player"))
				.isInstanceOf(ClassNotFoundException.class);
		assertThatThrownBy(() -> Class.forName("com.scrim.lolscrim.domain.player.RiotAccount"))
				.isInstanceOf(ClassNotFoundException.class);
	}

	private static Set<Class<?>> annotatedTypes(Class<? extends Annotation> annotationType) {
		ClassPathScanningCandidateComponentProvider scanner =
				new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(annotationType));
		return scanner.findCandidateComponents(BASE_PACKAGE).stream()
				.map(candidate -> load(candidate.getBeanClassName()))
				.collect(java.util.stream.Collectors.toSet());
	}

	private static Class<?> load(String className) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
