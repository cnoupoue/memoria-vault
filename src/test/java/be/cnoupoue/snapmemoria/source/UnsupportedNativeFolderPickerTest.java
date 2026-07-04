package be.cnoupoue.snapmemoria.source;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UnsupportedNativeFolderPickerTest {

  @Test
  void unsupportedPickerReturnsSafeStructuredError() {
    assertThatThrownBy(() -> new UnsupportedNativeFolderPicker().selectFolder())
        .isInstanceOf(FolderPickerUnavailableException.class)
        .hasMessage(FolderPickerUnavailableException.MESSAGE);
  }
}
