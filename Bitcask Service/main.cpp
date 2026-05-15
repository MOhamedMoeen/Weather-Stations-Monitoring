#include "include/Segment.h"

int main() {
    string path = "data.bin";
    Segment seg(path, 1);
    //
    // string key = "name2";
    // string value = "mohamed";
    //
    // ll offset = seg.write(key, value, time(0));
    //
    // cout << "Offset = " << offset << endl;

    cout << seg.read(33, 5) << endl;
}