package com.easyeat.dish.repository;

import com.easyeat.dish.domain.DishPhoto;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishRecipeIngredientId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("Dish Repositories Interface & Contract Tests")
class DishRepositoriesInterfaceTest {

    @Nested
    @DisplayName("DishPhotoRepository contract tests")
    class DishPhotoRepositoryTests {

        @Test
        @DisplayName("Should be an interface annotated with @Repository and extend JpaRepository<DishPhoto, UUID>")
        void shouldBeProperJpaRepositoryInterface() {
            Class<?> clazz = DishPhotoRepository.class;

            assertThat(clazz.isInterface()).isTrue();
            assertThat(clazz.isAnnotationPresent(Repository.class)).isTrue();
            assertThat(JpaRepository.class.isAssignableFrom(clazz)).isTrue();

            Type[] genericInterfaces = clazz.getGenericInterfaces();
            boolean hasExpectedJpaRepoGeneric = false;
            for (Type type : genericInterfaces) {
                if (type instanceof ParameterizedType pt && pt.getRawType().equals(JpaRepository.class)) {
                    Type[] typeArgs = pt.getActualTypeArguments();
                    if (typeArgs.length == 2 && typeArgs[0].equals(DishPhoto.class) && typeArgs[1].equals(UUID.class)) {
                        hasExpectedJpaRepoGeneric = true;
                    }
                }
            }
            assertThat(hasExpectedJpaRepoGeneric)
                    .as("DishPhotoRepository must extend JpaRepository<DishPhoto, UUID>")
                    .isTrue();
        }

        @Test
        @DisplayName("Should declare findByDishId returning Optional<DishPhoto>")
        void shouldDeclareFindByDishId() throws NoSuchMethodException {
            Method method = DishPhotoRepository.class.getMethod("findByDishId", UUID.class);
            assertThat(method.getReturnType()).isEqualTo(Optional.class);

            Type returnType = method.getGenericReturnType();
            assertThat(returnType).isInstanceOf(ParameterizedType.class);
            Type[] typeArgs = ((ParameterizedType) returnType).getActualTypeArguments();
            assertThat(typeArgs).containsExactly(DishPhoto.class);
        }

        @Test
        @DisplayName("Should declare existsByDishId returning boolean")
        void shouldDeclareExistsByDishId() throws NoSuchMethodException {
            Method method = DishPhotoRepository.class.getMethod("existsByDishId", UUID.class);
            assertThat(method.getReturnType()).isEqualTo(boolean.class);
        }

        @Test
        @DisplayName("Should declare deleteByDishId returning void")
        void shouldDeclareDeleteByDishId() throws NoSuchMethodException {
            Method method = DishPhotoRepository.class.getMethod("deleteByDishId", UUID.class);
            assertThat(method.getReturnType()).isEqualTo(void.class);
        }

        @Test
        @DisplayName("Should be invocable via mockito proxy")
        void shouldBeInvocableViaMock() {
            DishPhotoRepository mockRepo = Mockito.mock(DishPhotoRepository.class);
            UUID dishId = UUID.randomUUID();
            DishPhoto photo = new DishPhoto(dishId, "photo.jpg", "image/jpeg", 1024L, new byte[]{1, 2});

            when(mockRepo.findByDishId(dishId)).thenReturn(Optional.of(photo));
            when(mockRepo.existsByDishId(dishId)).thenReturn(true);
            doNothing().when(mockRepo).deleteByDishId(dishId);

            assertThat(mockRepo.findByDishId(dishId)).contains(photo);
            assertThat(mockRepo.existsByDishId(dishId)).isTrue();
            mockRepo.deleteByDishId(dishId);

            verify(mockRepo, times(1)).findByDishId(dishId);
            verify(mockRepo, times(1)).existsByDishId(dishId);
            verify(mockRepo, times(1)).deleteByDishId(dishId);
        }
    }

    @Nested
    @DisplayName("DishRecipeIngredientRepository contract tests")
    class DishRecipeIngredientRepositoryTests {

        @Test
        @DisplayName("Should be an interface annotated with @Repository and extend JpaRepository<DishRecipeIngredient, DishRecipeIngredientId>")
        void shouldBeProperJpaRepositoryInterface() {
            Class<?> clazz = DishRecipeIngredientRepository.class;

            assertThat(clazz.isInterface()).isTrue();
            assertThat(clazz.isAnnotationPresent(Repository.class)).isTrue();
            assertThat(JpaRepository.class.isAssignableFrom(clazz)).isTrue();

            Type[] genericInterfaces = clazz.getGenericInterfaces();
            boolean hasExpectedJpaRepoGeneric = false;
            for (Type type : genericInterfaces) {
                if (type instanceof ParameterizedType pt && pt.getRawType().equals(JpaRepository.class)) {
                    Type[] typeArgs = pt.getActualTypeArguments();
                    if (typeArgs.length == 2 && typeArgs[0].equals(DishRecipeIngredient.class) && typeArgs[1].equals(DishRecipeIngredientId.class)) {
                        hasExpectedJpaRepoGeneric = true;
                    }
                }
            }
            assertThat(hasExpectedJpaRepoGeneric)
                    .as("DishRecipeIngredientRepository must extend JpaRepository<DishRecipeIngredient, DishRecipeIngredientId>")
                    .isTrue();
        }

        @Test
        @DisplayName("Should declare findByIdDishId returning List<DishRecipeIngredient>")
        void shouldDeclareFindByIdDishId() throws NoSuchMethodException {
            Method method = DishRecipeIngredientRepository.class.getMethod("findByIdDishId", UUID.class);
            assertThat(method.getReturnType()).isEqualTo(List.class);

            Type returnType = method.getGenericReturnType();
            assertThat(returnType).isInstanceOf(ParameterizedType.class);
            Type[] typeArgs = ((ParameterizedType) returnType).getActualTypeArguments();
            assertThat(typeArgs).containsExactly(DishRecipeIngredient.class);
        }

        @Test
        @DisplayName("Should declare deleteByIdDishId returning void")
        void shouldDeclareDeleteByIdDishId() throws NoSuchMethodException {
            Method method = DishRecipeIngredientRepository.class.getMethod("deleteByIdDishId", UUID.class);
            assertThat(method.getReturnType()).isEqualTo(void.class);
        }

        @Test
        @DisplayName("Should declare deleteByIdDishIdAndIdIngredientId returning void")
        void shouldDeclareDeleteByIdDishIdAndIdIngredientId() throws NoSuchMethodException {
            Method method = DishRecipeIngredientRepository.class.getMethod("deleteByIdDishIdAndIdIngredientId", UUID.class, UUID.class);
            assertThat(method.getReturnType()).isEqualTo(void.class);
        }

        @Test
        @DisplayName("Should declare findByIdDishIdAndIdIngredientId returning Optional<DishRecipeIngredient>")
        void shouldDeclareFindByIdDishIdAndIdIngredientId() throws NoSuchMethodException {
            Method method = DishRecipeIngredientRepository.class.getMethod("findByIdDishIdAndIdIngredientId", UUID.class, UUID.class);
            assertThat(method.getReturnType()).isEqualTo(Optional.class);

            Type returnType = method.getGenericReturnType();
            assertThat(returnType).isInstanceOf(ParameterizedType.class);
            Type[] typeArgs = ((ParameterizedType) returnType).getActualTypeArguments();
            assertThat(typeArgs).containsExactly(DishRecipeIngredient.class);
        }

        @Test
        @DisplayName("Should declare existsByIdDishIdAndIdIngredientId returning boolean")
        void shouldDeclareExistsByIdDishIdAndIdIngredientId() throws NoSuchMethodException {
            Method method = DishRecipeIngredientRepository.class.getMethod("existsByIdDishIdAndIdIngredientId", UUID.class, UUID.class);
            assertThat(method.getReturnType()).isEqualTo(boolean.class);
        }

        @Test
        @DisplayName("Should declare countByIdDishId returning long")
        void shouldDeclareCountByIdDishId() throws NoSuchMethodException {
            Method method = DishRecipeIngredientRepository.class.getMethod("countByIdDishId", UUID.class);
            assertThat(method.getReturnType()).isEqualTo(long.class);
        }

        @Test
        @DisplayName("Should be invocable via mockito proxy")
        void shouldBeInvocableViaMock() {
            DishRecipeIngredientRepository mockRepo = Mockito.mock(DishRecipeIngredientRepository.class);
            UUID dishId = UUID.randomUUID();
            UUID ingredientId = UUID.randomUUID();
            DishRecipeIngredient item = new DishRecipeIngredient(dishId, ingredientId, new BigDecimal("150.0000"));

            when(mockRepo.findByIdDishId(dishId)).thenReturn(List.of(item));
            when(mockRepo.findByIdDishIdAndIdIngredientId(dishId, ingredientId)).thenReturn(Optional.of(item));
            when(mockRepo.existsByIdDishIdAndIdIngredientId(dishId, ingredientId)).thenReturn(true);
            when(mockRepo.countByIdDishId(dishId)).thenReturn(1L);
            doNothing().when(mockRepo).deleteByIdDishId(dishId);
            doNothing().when(mockRepo).deleteByIdDishIdAndIdIngredientId(dishId, ingredientId);

            assertThat(mockRepo.findByIdDishId(dishId)).containsExactly(item);
            assertThat(mockRepo.findByIdDishIdAndIdIngredientId(dishId, ingredientId)).contains(item);
            assertThat(mockRepo.existsByIdDishIdAndIdIngredientId(dishId, ingredientId)).isTrue();
            assertThat(mockRepo.countByIdDishId(dishId)).isEqualTo(1L);
            mockRepo.deleteByIdDishId(dishId);
            mockRepo.deleteByIdDishIdAndIdIngredientId(dishId, ingredientId);

            verify(mockRepo, times(1)).findByIdDishId(dishId);
            verify(mockRepo, times(1)).findByIdDishIdAndIdIngredientId(dishId, ingredientId);
            verify(mockRepo, times(1)).existsByIdDishIdAndIdIngredientId(dishId, ingredientId);
            verify(mockRepo, times(1)).countByIdDishId(dishId);
            verify(mockRepo, times(1)).deleteByIdDishId(dishId);
            verify(mockRepo, times(1)).deleteByIdDishIdAndIdIngredientId(dishId, ingredientId);
        }
    }
}
