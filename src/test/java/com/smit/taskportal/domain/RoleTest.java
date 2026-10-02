package com.smit.taskportal.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @Test
    void onlyManagerAndAdminAreManagerOrAbove() {
        assertThat(Role.CLIENT.isManagerOrAbove()).isFalse();
        assertThat(Role.ASSOCIATE.isManagerOrAbove()).isFalse();
        assertThat(Role.COORDINATOR.isManagerOrAbove()).isFalse();
        assertThat(Role.MANAGER.isManagerOrAbove()).isTrue();
        assertThat(Role.ADMIN.isManagerOrAbove()).isTrue();
    }

    @Test
    void onlyInternalRolesAreCoordinatorOrAbove() {
        assertThat(Role.CLIENT.isCoordinatorOrAbove()).isFalse();
        assertThat(Role.ASSOCIATE.isCoordinatorOrAbove()).isFalse();
        assertThat(Role.COORDINATOR.isCoordinatorOrAbove()).isTrue();
        assertThat(Role.MANAGER.isCoordinatorOrAbove()).isTrue();
        assertThat(Role.ADMIN.isCoordinatorOrAbove()).isTrue();
    }

    @Test
    void onlyAdminIsAdmin() {
        assertThat(Role.CLIENT.isAdmin()).isFalse();
        assertThat(Role.ASSOCIATE.isAdmin()).isFalse();
        assertThat(Role.COORDINATOR.isAdmin()).isFalse();
        assertThat(Role.MANAGER.isAdmin()).isFalse();
        assertThat(Role.ADMIN.isAdmin()).isTrue();
    }

    @Test
    void onlyTheExternalAccountIsAClient() {
        assertThat(Role.CLIENT.isClient()).isTrue();
        assertThat(Role.ASSOCIATE.isClient()).isFalse();
        assertThat(Role.COORDINATOR.isClient()).isFalse();
        assertThat(Role.MANAGER.isClient()).isFalse();
        assertThat(Role.ADMIN.isClient()).isFalse();
    }

    @Test
    void atLeastFollowsThePrivilegeOrder() {
        assertThat(Role.ADMIN.atLeast(Role.CLIENT)).isTrue();
        assertThat(Role.ADMIN.atLeast(Role.ASSOCIATE)).isTrue();
        assertThat(Role.ADMIN.atLeast(Role.COORDINATOR)).isTrue();
        assertThat(Role.ADMIN.atLeast(Role.MANAGER)).isTrue();
        assertThat(Role.MANAGER.atLeast(Role.CLIENT)).isTrue();
        assertThat(Role.MANAGER.atLeast(Role.ASSOCIATE)).isTrue();
        assertThat(Role.MANAGER.atLeast(Role.COORDINATOR)).isTrue();
        assertThat(Role.MANAGER.atLeast(Role.MANAGER)).isTrue();
        assertThat(Role.COORDINATOR.atLeast(Role.CLIENT)).isTrue();
        assertThat(Role.COORDINATOR.atLeast(Role.ASSOCIATE)).isTrue();
        assertThat(Role.COORDINATOR.atLeast(Role.COORDINATOR)).isTrue();
        assertThat(Role.ASSOCIATE.atLeast(Role.CLIENT)).isTrue();
        assertThat(Role.ASSOCIATE.atLeast(Role.ASSOCIATE)).isTrue();
        assertThat(Role.CLIENT.atLeast(Role.CLIENT)).isTrue();

        assertThat(Role.MANAGER.atLeast(Role.ADMIN)).isFalse();
        assertThat(Role.COORDINATOR.atLeast(Role.ADMIN)).isFalse();
        assertThat(Role.ASSOCIATE.atLeast(Role.ADMIN)).isFalse();
        assertThat(Role.CLIENT.atLeast(Role.ADMIN)).isFalse();
        assertThat(Role.COORDINATOR.atLeast(Role.MANAGER)).isFalse();
        assertThat(Role.ASSOCIATE.atLeast(Role.MANAGER)).isFalse();
        assertThat(Role.CLIENT.atLeast(Role.MANAGER)).isFalse();
        assertThat(Role.ASSOCIATE.atLeast(Role.COORDINATOR)).isFalse();
        assertThat(Role.CLIENT.atLeast(Role.COORDINATOR)).isFalse();
        assertThat(Role.CLIENT.atLeast(Role.ASSOCIATE)).isFalse();
    }
}
