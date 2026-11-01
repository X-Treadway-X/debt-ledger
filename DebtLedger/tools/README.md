# Logic check

`LogicCheck.kt` tests the pure logic (merge rules, restore, balances, amount parsing) without Android.
It needs a JDK and a Kotlin compiler (`kotlinc`):

```bash
kotlinc app/src/main/kotlin/com/daftar/deyoun/Model.kt \
        app/src/main/kotlin/com/daftar/deyoun/Merge.kt \
        tools/LogicCheck.kt -include-runtime -d /tmp/logiccheck.jar
java -jar /tmp/logiccheck.jar
```

Expected last line: `ALL PASSED`
