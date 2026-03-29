# This script runs the test for decompiling class-level constructs on a single test .class file.

# To use it:
#   - First compile desired .java test file into .class files.
#   - Then, from project root, run `bash run_class_single_test.sh <path to .class file>`.
#   - Example: bash run_class_single_test.sh 'src/test/resources/SampleAbstractClass.class'
# REMEMBER TO RE-COMPILE .JAVA INTO .CLASS WHENEVER YOU MODIFY TEST JAVA FILES!
# See https://github.com/adamsmd/jade/pull/2 for manual equivalence

if [ "$#" -ne 1 ]; then
    echo "Error: Wrong number of arguments."
    echo "Usage: bash $0 <path to .class file>"
    exit 1
fi

rm -rf tmp
mkdir tmp

single_class_file_path=$1

basename="${single_class_file_path##*/}"
echo "-----------------------------"
echo "Decompiling ${single_class_file_path}"

# Find related inner class files (e.g. Foo$Bar.class for Foo.class)
dir=$(dirname "${single_class_file_path}")
class_name="${basename%.class}"
# inner_class_files=$(find "${dir}" -name "${class_name}\$*.class" 2>/dev/null | tr '\n' ' ')
inner_class_files=$(find "${dir}" -name "${class_name}\$*.class" 2>/dev/null | tr '\n' ' ')
all_class_files="${single_class_file_path} ${inner_class_files}"

# Start timer
start=$SECONDS

# decompile class files (include inner class files so they can be nested)
# ./gradlew run --args="--log=debug decompile ${single_class_file_path} tmp" > out.txt

# V1: original single file decompilation
# ./gradlew run --args="decompile ${single_class_file_path} tmp"

# V2: include inner class files
./gradlew run --args="decompile ${all_class_files} tmp"

echo "Done decompiling ${basename}"
cd tmp

# Compile newly-obtained java file into .class file
# If there is any dependency, include them with a -classpath parameter instead, for example
# javac -classpath denpendency1.jar:dependency2.jar:dependency3.jar: "${basename%*.class}.java"
echo "Recompiling ${basename}"
javac "${basename%*.class}.java"

echo "Done recompiling ${basename}"
echo ""

# Compare class skeletons of old and new bytecodes
echo "Diff for ${basename}:"

echo "    Original class file path: ${single_class_file_path}"
echo "    Current class file path: tmp/${basename}"

# Return to root folder
cd ..

# Run diff command and capture their output.
# This script currently only check if the class skeletons are the same, i.e. output of running javap -p -s on the class files are the same
javap -p -s ${single_class_file_path} > 1.txt
javap -p -s tmp/${basename} > 2.txt
diff_output=$(diff 1.txt 2.txt)

# Add output to file
if [ -z "$diff_output" ]; then
    echo "No mismatch between ${single_class_file_path} and tmp/${basename}"
else
    echo "There's mismatch: $diff_output"
fi

# Display elapsed time
elapsed=$((SECONDS - start))
echo "Time elapsed: $elapsed seconds"

count=$((count+1))
echo "Processed $count files"

rm -f 1.txt
rm -f 2.txt