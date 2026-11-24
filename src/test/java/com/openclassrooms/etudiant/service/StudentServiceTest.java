package com.openclassrooms.etudiant.service;

import com.openclassrooms.etudiant.dto.StudentRequestDTO;
import com.openclassrooms.etudiant.dto.StudentResponseDTO;
import com.openclassrooms.etudiant.entities.Student;
import com.openclassrooms.etudiant.mapper.StudentDtoMapper;
import com.openclassrooms.etudiant.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {
    private static final StudentResponseDTO CAMILLE =
            new StudentResponseDTO(42L, "Camille", "Martin", "camille.martin@example.org");

    @Mock
    private StudentRepository studentRepository;

    private StudentService studentService;

    @BeforeEach
    void setUp() {
        studentService = new StudentService(studentRepository, Mappers.getMapper(StudentDtoMapper.class));
    }

    @Test
    void returnsCreatedStudentWithAssignedId() {
        StudentRequestDTO request = new StudentRequestDTO(CAMILLE.firstName(), CAMILLE.lastName(), CAMILLE.email());
        // Simulate the ID assigned by MySQL to the submitted student.
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> {
            Student student = invocation.getArgument(0);
            student.setId(42L);
            return student;
        });

        assertThat(studentService.create(request)).isEqualTo(CAMILLE);
    }

    @Test
    void returnsStudentsAsDtos() {
        StudentResponseDTO alex = new StudentResponseDTO(7L, "Alex", "Durand", "alex.durand@example.org");
        // The repository returns entities ordered by ID.
        when(studentRepository.findAll(Sort.by("id"))).thenReturn(List.of(storedStudent(alex), storedStudent(CAMILLE)));

        assertThat(studentService.findAll()).containsExactly(alex, CAMILLE);
    }

    @Test
    void returnsStudentById() {
        // The requested student already exists in the repository.
        when(studentRepository.findById(42L)).thenReturn(Optional.of(storedStudent(CAMILLE)));

        assertThat(studentService.findById(42L)).isEqualTo(CAMILLE);
    }

    @Test
    void returnsUpdatedStudentWithSameId() {
        when(studentRepository.findById(42L)).thenReturn(Optional.of(storedStudent(CAMILLE)));
        StudentRequestDTO changes = new StudentRequestDTO("Camille", "Martin", "camille.durand@example.org");

        StudentResponseDTO updated = studentService.update(42L, changes);

        // The returned DTO keeps the original ID and includes the new address.
        assertThat(updated).isEqualTo(new StudentResponseDTO(42L, "Camille", "Martin", changes.email()));
    }

    private static Student storedStudent(StudentResponseDTO data) {
        Student student = new Student();
        student.setId(data.id());
        student.setFirstName(data.firstName());
        student.setLastName(data.lastName());
        student.setEmail(data.email());
        return student;
    }
}
