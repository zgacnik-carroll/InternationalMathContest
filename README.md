# International Math Contest CSV Splitter

---

This repository contains a Java program that splits a contest results CSV into:

- `Institutions.csv`
- `Teams.csv`

The program is designed for the 2015 input file in this repository and for other yearly files that use the same column layout.

---

## Requirements

- Java JDK 21.0.7

---

## Compile

From the repository root:

```powershell
javac src/ContestResultsSplitter.java
```

---

## Usage

If you run the program with no arguments, it defaults to `2015.csv` and writes `Institutions.csv` and `Teams.csv` in the project root:

```powershell
java -cp src ContestResultsSplitter
```

You can also run the program with an explicit source CSV path:

```powershell
java -cp src ContestResultsSplitter 2015.csv
```

Both forms write:

- `Institutions.csv`
- `Teams.csv`

You can also choose custom output paths:

```powershell
java -cp src ContestResultsSplitter 2016.csv output/Institutions.csv output/Teams.csv
```

---

## Output format

`Institutions.csv` contains:

- `Institution ID`
- `Institution Name`
- `City`
- `State/Province`
- `Country`

`Teams.csv` contains:

- `Team Number`
- `Advisor`
- `Problem`
- `Ranking`
- `Institution ID`

---

## Notes

- Institution IDs are generated sequentially as `INST0001`, `INST0002`, and so on.
- Institutions are treated as distinct by the combination of institution name, city, state/province, and country.
- The program strips the malformed BOM characters present in the provided `2015.csv` header before validating columns.
- The implementation is self-contained and uses only the Java standard library.
