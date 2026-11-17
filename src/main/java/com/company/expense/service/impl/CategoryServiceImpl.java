package com.company.expense.service.impl;

import com.company.expense.dto.request.CreateCategoryRequest;
import com.company.expense.dto.request.UpdateCategoryRequest;
import com.company.expense.dto.response.CategoryResponse;
import com.company.expense.entity.ExpenseCategory;
import com.company.expense.exception.DuplicateResourceException;
import com.company.expense.exception.ResourceNotFoundException;
import com.company.expense.repository.ExpenseCategoryRepository;
import com.company.expense.service.CategoryService;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final ExpenseCategoryRepository categoryRepository;

    public CategoryServiceImpl(ExpenseCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        ExpenseCategory category = new ExpenseCategory(
                request.name(), request.description(), request.requiresReceiptOrDefault());
        if (categoryRepository.existsByName(category.getName())) {
            throw new DuplicateResourceException("Category " + category.getName() + " already exists");
        }
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Override
    public CategoryResponse updateCategory(Long categoryId, UpdateCategoryRequest request) {
        ExpenseCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
        category.update(request.description(), request.requiresReceipt(), request.active());
        return CategoryResponse.from(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> listAllCategories() {
        return categoryRepository.findAll(Sort.by("name")).stream().map(CategoryResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> listActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByName().stream().map(CategoryResponse::from).toList();
    }
}
