# Distributed Systems Final Report

## General Information
This is the template repository for the final report of the Distributed Systems course, Master's Degree in Computer Science and Engineering, University of Bologna, Campus of Cesena, Italy.

### Instructions
1. Click on the green button "Use this template" to create a new repository from this template.
2. Clone the new repository to your local machine.
3. Look at the structure of the repository and understand the purpose of each file, especially if you are not familiar with LaTeX.

### What's happening?
There is a GitHub Action that compiles the LaTeX files in the repository and creates a PDF file.
The PDF file is then uploaded as an artifact of the action.
The action is triggered on every push to the repository on specific branches (see `.github/workflows/compile.yml`).

In the action, there is also a step that improves the bibliographic references in the LaTeX files.
If you are interested in how it works, look at the script `scripts/bibtex_prettifier.rb`.

If the build fails, then it is very likely that your LaTeX files contain errors.
*READ* the error messages in the action logs to understand what is wrong.

### How to write the report
1. Edit the file `distributed-systems-final-report.tex`.
2. You can add new images in the `figures` directory.
3. If needed, you can create a `tables` directory and put your tables in it.
4. The general structure of the report is already defined in the `distributed-systems-final-report.tex` file.