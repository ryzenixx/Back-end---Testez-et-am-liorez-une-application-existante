package com.openclassrooms.etudiant.service;

import com.openclassrooms.etudiant.dto.StudentRequestDTO;
import com.openclassrooms.etudiant.dto.StudentResponseDTO;
import com.openclassrooms.etudiant.entities.Student;
import com.openclassrooms.etudiant.exception.StudentNotFoundException;
import com.openclassrooms.etudiant.mapper.StudentDtoMapper;
import com.openclassrooms.etudiant.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentService {
    private final StudentRepository studentRepository;
    private final StudentDtoMapper studentDtoMapper;

    @Transactional
    public StudentResponseDTO create(StudentRequestDTO request) {
        Student student = studentRepository.save(studentDtoMapper.toEntity(request));
        return studentDtoMapper.toResponse(student);
    }

    public List<StudentResponseDTO> findAll() {
        return studentRepository.findAll(Sort.by("id")).stream()
                .map(studentDtoMapper::toResponse)
                .toList();
    }

    public StudentResponseDTO findById(long id) {
        return studentDtoMapper.toResponse(getStudent(id));
    }

    @Transactional
    public StudentResponseDTO update(long id, StudentRequestDTO request) {
        Student student = getStudent(id);
        studentDtoMapper.updateEntity(request, student);
        return studentDtoMapper.toResponse(student);
    }

    @Transactional
    public void delete(long id) {
        studentRepository.delete(getStudent(id));
    }

    private Student getStudent(long id) {
        return studentRepository.findById(id).orElseThrow(() -> new StudentNotFoundException(id));
    }
}
