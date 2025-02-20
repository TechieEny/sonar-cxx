/*
 * C++ Community Plugin (cxx plugin)
 * Copyright (C) 2010-2022 SonarOpenCommunity
 * http://github.com/SonarOpenCommunity/sonar-cxx
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.cxx.preprocessor;

import com.sonar.cxx.sslr.api.AstNode;
import com.sonar.cxx.sslr.api.Grammar;
import com.sonar.cxx.sslr.impl.Parser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.sonar.cxx.squidbridge.SquidAstVisitorContext;

class PPIncludeTest {

  private CxxPreprocessor pp;
  private PPInclude include;
  private Parser<Grammar> lineParser;
  private Path foo;

  private final File expected1 = new File(new File("src/test/resources/codeprovider/source.hh").getAbsolutePath());
  private final File expected2 = new File(new File("src/test/resources/codeprovider/source").getAbsolutePath());
  private final File root = new File(new File("src/test/resources/codeprovider").getAbsolutePath());

  @TempDir
  File tempDir;

  @BeforeEach
  void setUp() throws IOException {
    var file = new File("dummy");
    var context = mock(SquidAstVisitorContext.class);
    when(context.getFile()).thenReturn(file); // necessary for init
    pp = new CxxPreprocessor(context);
    pp.init();
    include = new PPInclude(pp, file);
    lineParser = PPParser.create(Charset.defaultCharset());

    // create include file
    Path root = tempDir.toPath();
    foo = Files.createFile(root.resolve("foo.h"));
  }

  @Test
  void testFindIncludedFileQuoted() {
    AstNode ast = lineParser.parse("#include " + "\"" + foo.toAbsolutePath().toString() + "\"");
    File result = include.findFile(ast);
    assertThat(result).isEqualTo(foo.toFile());
  }

  @Test
  void testFindIncludedFileBracketed() {
    AstNode ast = lineParser.parse("#include " + "<" + foo.toAbsolutePath().toString() + ">");
    File result = include.findFile(ast);
    assertThat(result).isEqualTo(foo.toFile());
  }

  // ////////////////////////////////////////////////////////////////////////////
  // Behavior in the absolute path case
  @Test
  void getting_file_with_abspath() {
    // lookup with absolute paths should ignore the value of current
    // working directory and should work the same in the quoted and
    // unquoted case
    include = new PPInclude(pp, new File("src/test/resources/codeprovider/dummy.cpp"));
    String path = expected1.getAbsolutePath();

    assertThat(include.getSourceCodeFile(path, true)).isEqualTo(expected1);
    assertThat(include.getSourceCodeFile(path, false)).isEqualTo(expected1);
    assertThat(include.getSourceCodeFile(path, true)).isEqualTo(expected1);
    assertThat(include.getSourceCodeFile(path, false)).isEqualTo(expected1);
  }

  // ////////////////////////////////////////////////////////////////////////////
  // Relpathes, standard behavior: equal for quoted and angle cases
  // We have following cases here:
  // Include string form | Include root form |
  //           | absolute | relative |
  // simple    | 1        | 2        |
  // -------------------------------------------
  // rel. path | 3        | 4        |
  @Test
  void getting_file_relpath_case1() {
    include = new PPInclude(pp, new File("dummy"));

    var includeRoot = Paths.get("src/test/resources/codeprovider").toAbsolutePath().toString();
    String baseDir = new File("src/test").getAbsolutePath();
    include.setIncludeRoots(Arrays.asList(includeRoot), baseDir);

    var path = "source.hh";
    assertThat(include.getSourceCodeFile(path, true)).isEqualTo(expected1);
    assertThat(include.getSourceCodeFile(path, false)).isEqualTo(expected1);
  }

  @Test
  void getting_file_relpath_case1_without_extension() {
    include = new PPInclude(pp, new File("dummy"));

    var includeRoot = Paths.get("src/test/resources/codeprovider").toAbsolutePath().toString();
    String baseDir = new File("src/test").getAbsolutePath();
    include.setIncludeRoots(Arrays.asList(includeRoot), baseDir);

    var path = "source";
    assertThat(include.getSourceCodeFile(path, true)).isEqualTo(expected2);
    assertThat(include.getSourceCodeFile(path, false)).isEqualTo(expected2);
  }

  @Test
  void getting_file_relpath_case2() {
    include = new PPInclude(pp, new File("dummy"));

    var includeRoot = Paths.get("resources/codeprovider").toString();
    String baseDir = new File("src/test").getAbsolutePath();
    include.setIncludeRoots(Arrays.asList(includeRoot), baseDir);

    var path = "source.hh";
    assertThat(include.getSourceCodeFile(path, true)).isEqualTo(expected1);
    assertThat(include.getSourceCodeFile(path, false)).isEqualTo(expected1);
  }

  @Test
  void getting_file_relpath_case2_without_extension() {
    include = new PPInclude(pp, new File("dummy"));

    var includeRoot = Paths.get("resources/codeprovider").toString();
    String baseDir = new File("src/test").getAbsolutePath();
    include.setIncludeRoots(Arrays.asList(includeRoot), baseDir);

    var path = "source";
    assertThat(include.getSourceCodeFile(path, true)).isEqualTo(expected2);
    assertThat(include.getSourceCodeFile(path, false)).isEqualTo(expected2);
  }

  @Test
  void getting_file_relpath_case3() {
    include = new PPInclude(pp, new File("dummy"));

    var includeRoot = Paths.get("src/test/resources").toAbsolutePath().toString();
    String baseDir = new File("src/test").getAbsolutePath();
    include.setIncludeRoots(Arrays.asList(includeRoot), baseDir);

    var path = "codeprovider/source.hh";
    assertThat(include.getSourceCodeFile(path, true)).isEqualTo(expected1);
    assertThat(include.getSourceCodeFile(path, false)).isEqualTo(expected1);
  }

  @Test
  void getting_file_relpath_case3_without_extension() {
    include = new PPInclude(pp, new File("dummy"));

    var includeRoot = Paths.get("src/test/resources").toAbsolutePath().toString();
    String baseDir = new File("src/test").getAbsolutePath();
    include.setIncludeRoots(Arrays.asList(includeRoot), baseDir);

    var path = "codeprovider/source";
    assertThat(include.getSourceCodeFile(path, true)).isEqualTo(expected2);
    assertThat(include.getSourceCodeFile(path, false)).isEqualTo(expected2);
  }

  @Test
  void getting_file_relpath_case4() {
    include = new PPInclude(pp, new File("dummy"));

    var includeRoot = Paths.get("resources").toString();
    String baseDir = new File("src/test").getAbsolutePath();
    include.setIncludeRoots(Arrays.asList(includeRoot), baseDir);

    var path = "codeprovider/source.hh";
    assertThat(include.getSourceCodeFile(path, true)).isEqualTo(expected1);
    assertThat(include.getSourceCodeFile(path, false)).isEqualTo(expected1);
  }

  @Test
  void getting_file_relpath_case4_without_extension() {
    include = new PPInclude(pp, new File("dummy"));

    var includeRoot = Paths.get("resources").toString();
    String baseDir = new File("src/test").getAbsolutePath();
    include.setIncludeRoots(Arrays.asList(includeRoot), baseDir);

    var path = "codeprovider/source";
    assertThat(include.getSourceCodeFile(path, true)).isEqualTo(expected2);
    assertThat(include.getSourceCodeFile(path, false)).isEqualTo(expected2);
  }

  // ////////////////////////////////////////////////////////////////////////////
  // Special behavior in the quoted case
  // Lookup in the current directory. Has to fail for the angle case
  @Test
  void getting_file_with_filename_and_cwd() {
    include = new PPInclude(pp, new File("src/test/resources/codeprovider/dummy.cpp"));

    var path = "source.hh";
    assertThat(include.getSourceCodeFile(path, true)).isEqualTo(expected1);
    assertThat(include.getSourceCodeFile(path, false)).isNull();
  }

  @Test
  void getting_file_with_relpath_and_cwd() {
    include = new PPInclude(pp, new File("src/test/resources/dummy.cpp"));

    var path = "codeprovider/source.hh";
    assertThat(include.getSourceCodeFile(path, true)).isEqualTo(expected1);
    assertThat(include.getSourceCodeFile(path, false)).isNull();
  }

  @Test
  void getting_file_with_relpath_containing_backsteps_and_cwd() {
    include = new PPInclude(pp, new File("src/test/resources/codeprovider/folder/dummy.cpp"));

    var path = "../source.hh";
    assertThat(include.getSourceCodeFile(path, true)).isEqualTo(expected1);
    assertThat(include.getSourceCodeFile(path, false)).isNull();
  }

  @Test
  void getting_source_code1() throws IOException {
    include = new PPInclude(pp, new File("dummy"));
    assertThat(include.getSourceCode(expected1, Charset.defaultCharset())).isEqualTo("source code");
  }

  @Test
  void getting_source_code2() throws IOException {
    include = new PPInclude(pp, new File("dummy"));
    assertThat(include.getSourceCode(expected2, Charset.defaultCharset())).isEqualTo("source code");
  }

  @Test
  void getting_source_code_utf_8() throws IOException {
    include = new PPInclude(pp, new File("dummy"));
    assertThat(include.getSourceCode(new File(root, "./utf-8.hh"),
                                     Charset.defaultCharset())).isEqualTo("UTF-8");
  }

  @Test
  void getting_source_code_utf_8_bom() throws IOException {
    include = new PPInclude(pp, new File("dummy"));
    assertThat(include.getSourceCode(new File(root, "./utf-8-bom.hh"),
                                     Charset.defaultCharset())).isEqualTo("UTF-8-BOM");
  }

  @Test
  void getting_source_code_utf_16_le_bom() throws IOException {
    include = new PPInclude(pp, new File("dummy"));
    assertThat(include.getSourceCode(new File(root, "./utf-16le-bom.hh"),
                                     Charset.defaultCharset())).isEqualTo("UTF-16LE-BOM");
  }

}
