# Environment variables
ANDROID_HOME ?=
KEYSTORE ?=
STOREPASS ?=
KEYPASS ?=
KEYALIAS ?=

# Build tools & SDK versions
build_tools := 24.0.3
target      := android-24

# Variables
project     := kelloradio
package     := net.kelloradio.app

src_dir     := src
res_dir     := res

lib_dir     := build/libraries
gen_dir     := build/generated
int_dir     := build/intermediate
out_dir     := build/output

sources     := $(shell find $(src_dir) -name '*.java')
resources   := $(shell find $(res_dir) -type f)
generated   := $(gen_dir)/net/kelloradio/app/R.java
libraries   :=

# Final zipaligned APK
$(out_dir)/$(project).apk: $(out_dir)/$(project)-unaligned.apk
	zipalign -f 4 $< $@

# Packaging the APK
$(out_dir)/$(project)-unaligned.apk: $(out_dir) AndroidManifest.xml $(resources) $(int_dir)/classes.dex
	aapt package -f \
		-M AndroidManifest.xml \
		-I $(ANDROID_HOME)/platforms/$(target)/android.jar \
		-S $(res_dir) \
		-F $@
	cd $(int_dir) && aapt add $(abspath $@) classes.dex
	jarsigner -verbose \
		-keystore $(KEYSTORE)  \
		-storepass $(STOREPASS) \
		-keypass $(KEYPASS) \
		$@ \
		$(KEYALIAS)

# Compilation & dexing w/ Jack
$(int_dir)/classes.dex: $(int_dir) $(sources) $(generated) $(libraries)
	java -jar $(ANDROID_HOME)/build-tools/$(build_tools)/jack.jar \
		--classpath $(ANDROID_HOME)/platforms/$(target)/android.jar \
		$(foreach lib,$(libraries),--import $(lib)) \
		--output-dex $(int_dir) \
		$(sources) $(generated)

# Generating R.java based on the manifest and resources
$(generated): $(gen_dir) AndroidManifest.xml $(resources)
	aapt package -f \
		-M AndroidManifest.xml \
		-I $(ANDROID_HOME)/platforms/$(target)/android.jar \
		-S $(res_dir) \
		-J $(gen_dir) \
		-m

# Fetching dependencies from Maven Central
$(libraries):
	curl --silent \
		    --location \
        --output $@ \
        --create-dirs \
        http://search.maven.org/remotecontent?filepath=$(subst $(lib_dir)/,,$@)

# Subfolders in build/
$(gen_dir) $(out_dir) $(int_dir):
	mkdir -p $@

.PHONY: clean
clean:
	rm -rf build

.PHONY: install
install: $(out_dir)/$(project).apk
	adb install -r $<

.PHONY: uninstall
uninstall:
	adb uninstall $(package)

.PHONY: run
run:
	adb shell am start $(package)/.MainActivity
