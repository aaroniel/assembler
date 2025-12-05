; ==== ИНИЦИАЛИЗАЦИЯ РЕГИСТРОВ ====

load 1 6        ; R1 = адрес источника (6)

; ==== ИНИЦИАЛИЗАЦИЯ МАССИВА ИСТОЧНИКА memory[6..] ====

load 3 1
write 1 3 0

load 3 2
write 1 3 1

load 3 3
write 1 3 2

load 3 4
write 1 3 3

load 3 5
write 1 3 4

load 3 6
write 1 3 5

load 3 7
write 1 3 6

; ==== ВОЗВЕДЕНИЕ МАССИВА В СТЕПЕНЬ memory[6..] ====

pow 6 1 2 0
pow 7 1 2 1
pow 8 1 2 2
pow 9 1 2 3
pow 10 1 2 4
pow 11 1 2 5
pow 12 1 2 6

;======= ОЧИСТКА РЕГИСТРОВ =======
load 1 0
load 2 0
load 3 0
load 4 0