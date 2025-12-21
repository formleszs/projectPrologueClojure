cell(X, Y, empty) :- \+ wall(X, Y).
cell(X, Y, wall) :- wall(X, Y).

valid_move(X, Y) :-
    cell(X, Y, empty),
    within_bounds(X, Y).

within_bounds(X, Y) :-
    maze_size(Width, Height),
    X >= 0, X < Width,
    Y >= 0, Y < Height.

wall(0, 0). wall(1, 0). wall(2, 0).             wall(4, 0). wall(5, 0). wall(6, 0). wall(7, 0). wall(8, 0). wall(9, 0).
wall(0, 1).                                                                                                 wall(9, 1).
wall(0, 2).             wall(2, 2). wall(3, 2). wall(4, 2). wall(5, 2).             wall(7, 2).             wall(9, 2).
wall(0, 3).                                     wall(4, 3).                                                 wall(9, 3).
wall(0, 4). wall(1, 4). wall(2, 4).             wall(4, 4). wall(5, 4). wall(6, 4).             wall(8, 4). wall(9, 4).
wall(0, 5).                                                                                                 wall(9, 5).
wall(0, 6).             wall(2, 6). wall(3, 6). wall(4, 6). wall(5, 6).             wall(7, 6).             wall(9, 6).
wall(0, 7).             wall(2, 7).                                                 wall(7, 7).             wall(9, 7).
wall(0, 8).                                     wall(4, 8).             wall(6, 8).                         wall(9, 8).
wall(0, 9). wall(1, 9). wall(2, 9). wall(3, 9). wall(4, 9). wall(5, 9). wall(6, 9). wall(7, 9). wall(8, 9). wall(9, 9).

maze_size(10, 10).

exit(3, 0).