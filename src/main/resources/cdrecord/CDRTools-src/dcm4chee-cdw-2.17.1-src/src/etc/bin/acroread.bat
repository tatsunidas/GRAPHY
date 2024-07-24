ECHO OFF
REM Windows Batch Script to print PDF file by Adobe Acrobat Reader(TM)

REM Adjust path to Acrobat Reader Executable
SET ACROBAT.EXE=C:\Programme\Adobe\Reader 8.0\Reader\AcroRd32.exe

REM Execute Acrobat Reader and open Print Dialog:
REM Attention: Blocks until Acrobat Reader is closed by user 
REM "%ACROBAT.EXE%" /p %1

REM Print Label on default Printer without user interaction:
START "" "%ACROBAT.EXE%" /h /p %1

REM Print Label on specified Printer without user interaction:
REM START "" "%ACROBAT.EXE%" /t %1 printername drivername portname
REM printername - The name of your printer.
REM drivername  - Your printer driver name.
REM portname    - The printer port.
REM Hint: The values may appear at the testpage printed by the Windows Print Manager.  