package com.easyeat.dish.domain;

import jakarta.persistence.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DishPhotoTest {

    @Test
    @DisplayName("Default constructor initializes default timestamps and null fields")
    void defaultConstructor() {
        DishPhoto photo = new DishPhoto();

        assertThat(photo.getDishId()).isNull();
        assertThat(photo.getFileName()).isNull();
        assertThat(photo.getContentType()).isNull();
        assertThat(photo.getFileSize()).isNull();
        assertThat(photo.getData()).isNull();
        assertThat(photo.getCreatedAt()).isNotNull();
        assertThat(photo.getCreatedAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
        assertThat(photo.getUpdatedAt()).isNotNull();
        assertThat(photo.getUpdatedAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("Parameterized constructor sets all fields and initializes timestamps")
    void parameterizedConstructor() {
        UUID dishId = UUID.randomUUID();
        String fileName = "tacos_al_pastor.jpg";
        String contentType = "image/jpeg";
        Long fileSize = 204800L;
        byte[] data = new byte[]{1, 2, 3, 4, 5, -128, 127};

        DishPhoto photo = new DishPhoto(dishId, fileName, contentType, fileSize, data);

        assertThat(photo.getDishId()).isEqualTo(dishId);
        assertThat(photo.getFileName()).isEqualTo(fileName);
        assertThat(photo.getContentType()).isEqualTo(contentType);
        assertThat(photo.getFileSize()).isEqualTo(fileSize);
        assertThat(photo.getData()).isEqualTo(data);
        assertThat(photo.getCreatedAt()).isNotNull();
        assertThat(photo.getCreatedAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
        assertThat(photo.getUpdatedAt()).isNotNull();
        assertThat(photo.getUpdatedAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("Getters and setters correctly update and return field values")
    void gettersAndSetters() {
        DishPhoto photo = new DishPhoto();

        UUID dishId = UUID.randomUUID();
        String fileName = "ceviche_clasico.png";
        String contentType = "image/png";
        Long fileSize = 512000L;
        byte[] data = "binary-image-payload-test".getBytes();
        Instant customCreated = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant customUpdated = Instant.now().minus(1, ChronoUnit.DAYS);

        photo.setDishId(dishId);
        photo.setFileName(fileName);
        photo.setContentType(contentType);
        photo.setFileSize(fileSize);
        photo.setData(data);
        photo.setCreatedAt(customCreated);
        photo.setUpdatedAt(customUpdated);

        assertThat(photo.getDishId()).isEqualTo(dishId);
        assertThat(photo.getFileName()).isEqualTo(fileName);
        assertThat(photo.getContentType()).isEqualTo(contentType);
        assertThat(photo.getFileSize()).isEqualTo(fileSize);
        assertThat(photo.getData()).isEqualTo(data);
        assertThat(photo.getCreatedAt()).isEqualTo(customCreated);
        assertThat(photo.getUpdatedAt()).isEqualTo(customUpdated);
    }

    @Test
    @DisplayName("Byte array data integrity is preserved across mutations")
    void dataIntegrity() {
        DishPhoto photo = new DishPhoto();
        byte[] largeData = new byte[1024 * 100]; // 100 KB
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        photo.setData(largeData);
        assertThat(photo.getData()).hasSize(1024 * 100);
        assertThat(photo.getData()).isEqualTo(largeData);

        // Verify empty byte array
        byte[] emptyData = new byte[0];
        photo.setData(emptyData);
        assertThat(photo.getData()).isEmpty();
    }

    @Test
    @DisplayName("Lifecycle methods onCreate and onUpdate update timestamps properly")
    void lifecycleCallbacks() {
        DishPhoto photo = new DishPhoto();
        photo.setCreatedAt(null);
        photo.setUpdatedAt(null);

        photo.onCreate();

        assertThat(photo.getCreatedAt()).isNotNull();
        assertThat(photo.getCreatedAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
        assertThat(photo.getUpdatedAt()).isNotNull();
        assertThat(photo.getUpdatedAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));

        Instant previousUpdated = Instant.now().minus(10, ChronoUnit.SECONDS);
        photo.setUpdatedAt(previousUpdated);

        photo.onUpdate();

        assertThat(photo.getUpdatedAt()).isAfter(previousUpdated);
        assertThat(photo.getUpdatedAt()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("JPA annotations match specification mappings")
    void jpaAnnotations() throws NoSuchFieldException, NoSuchMethodException {
        // Entity annotation
        assertThat(DishPhoto.class.isAnnotationPresent(Entity.class)).isTrue();

        // Table annotation
        Table table = DishPhoto.class.getAnnotation(Table.class);
        assertThat(table).isNotNull();
        assertThat(table.name()).isEqualTo("dish_photos");

        // dishId field
        Field dishIdField = DishPhoto.class.getDeclaredField("dishId");
        assertThat(dishIdField.isAnnotationPresent(Id.class)).isTrue();
        Column dishIdCol = dishIdField.getAnnotation(Column.class);
        assertThat(dishIdCol.name()).isEqualTo("dish_id");
        assertThat(dishIdCol.nullable()).isFalse();

        // fileName field
        Field fileNameField = DishPhoto.class.getDeclaredField("fileName");
        Column fileNameCol = fileNameField.getAnnotation(Column.class);
        assertThat(fileNameCol.name()).isEqualTo("file_name");
        assertThat(fileNameCol.nullable()).isFalse();
        assertThat(fileNameCol.length()).isEqualTo(255);

        // contentType field
        Field contentTypeField = DishPhoto.class.getDeclaredField("contentType");
        Column contentTypeCol = contentTypeField.getAnnotation(Column.class);
        assertThat(contentTypeCol.name()).isEqualTo("content_type");
        assertThat(contentTypeCol.nullable()).isFalse();
        assertThat(contentTypeCol.length()).isEqualTo(50);

        // fileSize field
        Field fileSizeField = DishPhoto.class.getDeclaredField("fileSize");
        Column fileSizeCol = fileSizeField.getAnnotation(Column.class);
        assertThat(fileSizeCol.name()).isEqualTo("file_size");
        assertThat(fileSizeCol.nullable()).isFalse();

        // data field (LOB and BYTEA)
        Field dataField = DishPhoto.class.getDeclaredField("data");
        assertThat(dataField.isAnnotationPresent(Lob.class)).isTrue();
        Column dataCol = dataField.getAnnotation(Column.class);
        assertThat(dataCol.name()).isEqualTo("data");
        assertThat(dataCol.nullable()).isFalse();
        assertThat(dataCol.columnDefinition()).isEqualTo("BYTEA");

        // createdAt field
        Field createdAtField = DishPhoto.class.getDeclaredField("createdAt");
        Column createdAtCol = createdAtField.getAnnotation(Column.class);
        assertThat(createdAtCol.name()).isEqualTo("created_at");
        assertThat(createdAtCol.nullable()).isFalse();
        assertThat(createdAtCol.updatable()).isFalse();

        // updatedAt field
        Field updatedAtField = DishPhoto.class.getDeclaredField("updatedAt");
        Column updatedAtCol = updatedAtField.getAnnotation(Column.class);
        assertThat(updatedAtCol.name()).isEqualTo("updated_at");
        assertThat(updatedAtCol.nullable()).isFalse();

        // Callback annotations
        Method onCreateMethod = DishPhoto.class.getDeclaredMethod("onCreate");
        assertThat(onCreateMethod.isAnnotationPresent(PrePersist.class)).isTrue();

        Method onUpdateMethod = DishPhoto.class.getDeclaredMethod("onUpdate");
        assertThat(onUpdateMethod.isAnnotationPresent(PreUpdate.class)).isTrue();
    }
}
