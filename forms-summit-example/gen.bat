@ECHO OFF

REM
REM Use the Forms Generator to compile the necessary files.  You must have
REM already installed the SUMMIT schema in the database and also have the
REM Forms environment set.
REM
REM To run this batch file, you need to specify the username and connect
REM string.  There are two ways to do this:
REM   1. Modify the "set CONNSTRING=" line, below, and put in your connect
REM      string.  For exampe: set CONNSTRING=summit/summit@orcl
REM   2. Pass in the connect string on the command line.  For example:
REM        gen summit/summit@orcl
REM Note that if you have set the CONNSTRING variable, below, and also pass
REM in a connect string on the command line, the command line will override
REM the variable.
REM

if not "%1"=="" goto GEN1

REM
REM Set your connect string here, or pass it on the command line.
REM
set CONNSTRING=summit/summit

if "%CONNSTRING%"=="" goto ERROR

goto GEN2

:GEN1
set CONNSTRING=%1

:GEN2
echo Connect string is [%CONNSTRING%]
@ECHO ON

frmcmp batch=yes userid=%CONNSTRING% module_type=FORM module=customers
frmcmp batch=yes userid=%CONNSTRING% module_type=FORM module=dept     
frmcmp batch=yes userid=%CONNSTRING% module_type=FORM module=orders   
frmcmp batch=yes userid=%CONNSTRING% module_type=FORM module=pick     
frmcmp batch=yes userid=%CONNSTRING% module_type=FORM module=reg      

frmcmp batch=yes userid=%CONNSTRING% module_type=LIBRARY module=calendar
frmcmp batch=yes userid=%CONNSTRING% module_type=LIBRARY module=d2kdlstr     
frmcmp batch=yes userid=%CONNSTRING% module_type=LIBRARY module=picklist   
frmcmp batch=yes userid=%CONNSTRING% module_type=LIBRARY module=wizard     
frmcmp batch=yes userid=%CONNSTRING% module_type=LIBRARY module=gen     

frmcmp batch=yes userid=%CONNSTRING% module_type=MENU module=customers     
frmcmp batch=yes userid=%CONNSTRING% module_type=MENU module=m_summit     

echo Done

@ECHO OFF

REM
REM *DO NOT* modify the following line, it is there to clear the variable.
REM
set CONNSTRING=

goto end

:ERROR
echo ERROR: Use a text editor to open this file and read the comments at the
echo        top for instructions on how to run this batch file.

:END
