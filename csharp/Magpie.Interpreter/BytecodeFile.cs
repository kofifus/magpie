﻿using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;

namespace Magpie.Interpreter
{
    public class BytecodeFile
    {
        public byte[] Bytes { get { return mData; } }

        //### bob: hackish. should be able to look up export by name
        public int OffsetToMain
        {
            get
            {
                int offset = 16; // magic num + version + num exports + export name
                //### bob: hack temp
                return ((int)mData[offset++]) |
                       ((int)mData[offset++] << 8) |
                       ((int)mData[offset++] << 16) |
                       ((int)mData[offset++] << 24);
            }
        }

        public BytecodeFile(Stream stream)
        {
            // read the file
            mData = new byte[stream.Length];
            stream.Read(mData, 0, mData.Length);

            // check the header

            // magic number
            Expect(0, Encoding.ASCII.GetBytes(new char[] { 'p', 'i', 'e', '!' }));

            // version
            Expect(4, new byte[] { 0, 0, 0, 0 });

        }

        public string ReadString(int offset)
        {
            int start = offset;
            // find the end of the string
            while (mData[offset] != 0) offset++;

            return Encoding.UTF8.GetString(mData, start, offset - start);
        }

        private void Expect(int start, byte[] expected)
        {
            for (int i = 0; i < expected.Length; i++)
            {
                if ((byte)mData[start + i] != expected[i]) throw new Exception("Header doesn't match.");
            }
        }

        private byte[] mData;
    }
}