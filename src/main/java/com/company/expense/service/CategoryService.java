package com.company.expense.service;

import com.company.expense.dto.request.CreateCategoryRequest;
import com.company.expense.dto.request.UpdateCategoryRequest;
import com.company.expense.dto.response.CategoryResponse;
import java.util.List;

public interface CategoryService {

    CategoryResponse createCategory(CreateCategoryRequest request);

    CategoryResponse updateCategory(Long categoryId, UpdateCategoryRequest request);

    List<CategoryResponse> listAllCategories();

    List<CategoryResponse> listActiveCategories();
}
