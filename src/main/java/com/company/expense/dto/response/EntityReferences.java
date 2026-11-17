package com.company.expense.dto.response;

import com.company.expense.entity.BaseEntity;
import com.company.expense.entity.User;

final class EntityReferences {

    private EntityReferences() {
    }

    static Long idOf(BaseEntity entity) {
        return entity == null ? null : entity.getId();
    }

    static String nameOf(User user) {
        return user == null ? null : user.getFullName();
    }
}
