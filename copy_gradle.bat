@echo off
robocopy C:\Users\Admin\.gradle D:\.gradle /E /R:1 /W:1 /NFL /NDL /NJH > e:\Code\RikkaLLM\robocopy-gradle.log 2>&1
