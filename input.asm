; ==== ИНИЦИАЛИЗАЦИЯ РЕГИСТРОВ ====

load 1 6        ; R1 = адрес источника (6)
load 2 11        ; R2 = адрес назначения (11)

; ==== ИНИЦИАЛИЗАЦИЯ МАССИВА ИСТОЧНИКА memory[6..] ====

load 3 7
write 1 3 0

load 3 8
write 1 3 1

load 3 9
write 1 3 2


; ==== КОПИРОВАНИЕ МАССИВА ====
; R1 — адрес источника
; R2 — адрес приёмника
; R4 — буфер

; copy element 0
read 4 1 0
write 2 4 0

; copy element 1
read 4 1 1
write 2 4 1

; copy element 2
read 4 1 2
write 2 4 2

;======= ОЧИСТКА РЕГИСТРОВ =======
load 1 0
load 2 0
load 3 0
load 4 0